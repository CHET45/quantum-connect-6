"""Embedding-sum evaluation: the Java LearnedEval before folding (design 3.1-3.3, 4.4).

q = sum over windows (base[p] + stage[s, p] + cd[c, p]) + T1[c, t1] + T2[c, t2] + T3[t3], p = featMap[w, code_rel],
in logits; Java folds base + stage + cd per (stage, cd) context and stores round(256 * value) as int16.
"""
import torch


class Layout:
    def __init__(self, spec):
        self.spec = spec
        p = spec.window_params
        self.p = p
        self.base = 0
        self.stage = self.base + p
        self.cd = self.stage + spec.stages * p
        self.t1 = self.cd + spec.cd_contexts * p
        self.t2 = self.t1 + spec.cd_contexts * spec.t1
        self.t3 = self.t2 + spec.cd_contexts * spec.t2
        self.size = self.t3 + spec.t3


class Model(torch.nn.Module):
    def __init__(self, spec, device):
        super().__init__()
        self.layout = Layout(spec)
        self.theta = torch.nn.Parameter(torch.zeros(self.layout.size, device=device))
        self.win_cells = torch.as_tensor(spec.win_cells, device=device)  # [W, k]
        self.feat_map = torch.as_tensor(spec.feat_map, device=device).view(spec.windows, spec.codes)
        self.pow3 = torch.tensor([3 ** i for i in range(spec.k)], device=device, dtype=torch.int64)
        self.w_index = torch.arange(spec.windows, device=device)

    def window_index(self, cells, stm):
        """cells [B, cells] uint8 absolute, stm [B] 1 or 2 -> [B, W] parameter index within a window table."""
        c = cells.long()
        second = (stm == 2).view(-1, 1)
        rel = torch.where(second & (c > 0), 3 - c, c)
        codes = (rel[:, self.win_cells] * self.pow3).sum(-1)  # [B, W]
        return self.feat_map[self.w_index.view(1, -1), codes]

    def forward(self, cells, stm, stage, cd, t1, t2, t3):
        lay = self.layout
        idx = self.window_index(cells, stm)  # [B, W]
        th = self.theta
        stage = stage.long().view(-1, 1)
        cd = cd.long().view(-1, 1)
        q = th[lay.base + idx].sum(1)
        q = q + th[lay.stage + stage * lay.p + idx].sum(1)
        q = q + th[lay.cd + cd * lay.p + idx].sum(1)
        cdv = cd.view(-1)
        q = q + th[lay.t1 + cdv * lay.spec.t1 + t1.long()]
        q = q + th[lay.t2 + cdv * lay.spec.t2 + t2.long()]
        q = q + th[lay.t3 + t3.long()]
        return q

    def component_views(self):
        lay, s, th = self.layout, self.layout.spec, self.theta
        return {
            "base": th[lay.base:lay.stage],
            "stage": th[lay.stage:lay.cd].view(s.stages, lay.p),
            "cd": th[lay.cd:lay.t1].view(s.cd_contexts, lay.p),
            "t1": th[lay.t1:lay.t2].view(s.cd_contexts, s.t1),
            "t2": th[lay.t2:lay.t3].view(s.cd_contexts, s.t2),
            "t3": th[lay.t3:],
        }
