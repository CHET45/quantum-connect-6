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


def swap_index(spec):
    """Per window-table parameter: the parameter of the same window with the two colours exchanged, in the size-
    independent family (classes 0-5 are horizontal windows, whose canonical code is the smaller of code and reversed)."""
    codes, k = spec.codes, spec.k
    pow3 = [3 ** i for i in range(k)]
    swap = [0] * codes
    rev = [0] * codes
    for c in range(codes):
        d = [(c // pow3[i]) % 3 for i in range(k)]
        swap[c] = sum((3 - x if x else 0) * pow3[i] for i, x in enumerate(d))
        rev[c] = sum(x * pow3[k - 1 - i] for i, x in enumerate(d))
    table = []
    for cls in range(spec.classes):
        horizontal = cls < spec.classes // 3
        for c in range(codes):
            t = swap[c]
            table.append(cls * codes + (min(t, rev[t]) if horizontal else t))
    return table


class Model(torch.nn.Module):
    def __init__(self, spec, device, sym=None):
        """sym: None for free tables; else the share in [0, 1] kept of the part of the window tables that is even under
        exchanging the colours, so 0 makes them exactly antisymmetric and 1 is the free model."""
        super().__init__()
        self.layout = Layout(spec)
        self.sym = sym
        if sym is not None:
            lay = self.layout
            sw = torch.as_tensor(swap_index(spec), device=device, dtype=torch.int64)
            cd_swap = [0, 2, 1, 3]
            idx = [lay.base + sw]
            idx += [lay.stage + st * lay.p + sw for st in range(spec.stages)]
            idx += [lay.cd + cd_swap[c] * lay.p + sw for c in range(spec.cd_contexts)]
            idx.append(torch.arange(lay.t1, lay.size, device=device))
            self.swap_all = torch.cat(idx)
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

    def effective(self):
        """The parameters the evaluation uses: theta, or its colour-antisymmetric part plus a share of the even part."""
        if self.sym is None:
            return self.theta
        lay = self.layout
        n = lay.t1
        th = self.theta
        other = th[self.swap_all[:n]]
        win = 0.5 * (th[:n] - other) + 0.5 * self.sym * (th[:n] + other)
        return torch.cat([win, th[n:]])

    def forward(self, cells, stm, stage, cd, t1, t2, t3):
        lay = self.layout
        idx = self.window_index(cells, stm)  # [B, W]
        th = self.effective()
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
        lay, s, th = self.layout, self.layout.spec, self.effective()
        return {
            "base": th[lay.base:lay.stage],
            "stage": th[lay.stage:lay.cd].view(s.stages, lay.p),
            "cd": th[lay.cd:lay.t1].view(s.cd_contexts, lay.p),
            "t1": th[lay.t1:lay.t2].view(s.cd_contexts, s.t1),
            "t2": th[lay.t2:lay.t3].view(s.cd_contexts, s.t2),
            "t3": th[lay.t3:],
        }
