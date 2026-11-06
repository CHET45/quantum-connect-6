# Server games report

550 unique games (from 1127 lines in 3 export file(s), 552 unique ids, 2 dropped as same players and moves). Draws are not stored by the server, so every game here is decided and the W/L tables contain no draws. Plies are recorded moves (blasts included, skipped turns not); turns also count skipped turns.

## Notation and replay check

Replay with the engine rules (gravity, colour-blind six, 3x3 blast, a blast needs a cooldown counter of at least 10, every own turn, skipped ones included, counts toward it): **414 of 550 games consistent** (recorded winner is the replay winner, the line appears exactly at win_at, no earlier line, no disc into a full column, no blast before the cooldown).

- `2ec75f93-79c2-407f-964c-49d8d658e833` BeetaSaag vs Hurt me plenty: disc into a full column at ply 13
- `58eb1b3e-7d8c-496f-96a9-b5f3cadef25a` Hurt me plenty vs BeetaSaag: disc into a full column at ply 16
- `75a98193-e2f2-4291-b1e4-734f26b79bac` Savi5 beater 2.1 vs Hurt me plenty: column 14 outside the board at ply 3
- `096c50fd-1c9e-4706-ba26-15b3e0d1fcd6` BeetaSaag vs Savi5 beater 2.1: disc into a full column at ply 12
- `36cf60dd-2d2b-472b-bb05-fb5721889301` Savi5 beater 2.1 vs BeetaSaag: disc into a full column at ply 15
- `2f5bef3b-dc98-401e-8e94-5d6edb6c16fb` BeetaSaag vs Savi5 beater 2.1: disc into a full column at ply 9
- `303c82a5-5630-49fc-9770-3a96885a2bf8` Savi5 beater 2.1 vs BeetaSaag: column 12 outside the board at ply 13
- `b2ef288d-0b5a-4b02-b279-a5d5c8278cdd` BeetaSaag vs Savi5 beater 2.1: disc into a full column at ply 9
- `57debc9e-6c3f-4094-a4ab-6cae95d348f5` Savi5 beater 2.1 vs Testing_v2: column 12 outside the board at ply 4
- `c55c103f-137d-4db2-891c-fd41651e25f7` Testing_v2 vs Savi5 beater 2.1: column 15 outside the board at ply 2
- `60c8df89-eb6b-4cf7-8d9a-604a9730abb5` Savi5 beater 2.1 vs Testing_v2: column 12 outside the board at ply 7
- `3b3f78c6-ab09-43af-8500-a35e4b894744` Testing_v2 vs Savi5 beater 2.1: column 15 outside the board at ply 2
- `4ef8483c-797d-4b34-99c4-a05ce810154f` Savi5 beater 2.1 vs Testing_v2: column 15 outside the board at ply 2
- `4fe1ee34-3b01-4d0a-9199-b3cc6a0307d7` Testing_v2 vs Savi5 beater 2.1: column 12 outside the board at ply 4
- `aa63d840-7599-4d59-96fd-b70693204885` Savi5 beater 2.1 vs Hurt me plenty: column 12 outside the board at ply 41
- `b18da245-3754-4658-918b-302f003420c3` Savi5 beater 2.1 vs Hurt me plenty: column 12 outside the board at ply 3
- `c5a692c1-2750-4556-a5eb-a96c7649595a` Hurt me plenty vs Savi5 beater 2.1: blast on a full column at ply 32
- `7b76a278-9657-4b1f-a69f-27d3589420bd` Savi5 beater 2.1 vs Hurt me plenty: column 12 outside the board at ply 3
- `0d4ddd41-8e0b-4fa8-b526-ae3b4ca16d38` Savi5 beater 2.1 vs BeetaSaag: column 12 outside the board at ply 11
- `d9781c7f-72c1-4051-a1af-2c880c1c194d` Savi5 beater 2.1 vs BeetaSaag: disc into a full column at ply 12
- `14ab8c34-5c46-40be-a526-d762d62093ae` Strange Quark 1 vs Savi5 beater 2.1: column 12 outside the board at ply 12
- `3660c582-065c-40c1-ba32-a3f39610e8c8` Savi5 beater 2.1 vs Strange Quark 1: column 15 outside the board at ply 3
- `551feb3a-46c6-4d26-97a4-c2dd9b49a321` Strange Quark 1 vs Savi5 beater 2.1: disc into a full column at ply 13
- `4eaf3a5e-fa0c-474a-99e5-0cdd1b749a14` Hurt me plenty vs Strange Quark 1: disc into a full column at ply 14
- `9456db3f-c207-4ee4-9b49-e740e95eb504` Strange Quark 1 vs Hurt me plenty: disc into a full column at ply 13
- `1838ad20-e24e-49ea-b67f-bd2c37e6c5a2` Savi5 beater 2.1 vs Strange Quark 1: disc into a full column at ply 15
- `6ff4b1ee-974e-4c1c-88f7-c190586d4abb` specialmovedestroyer vs Savi5 beater 2.1: disc into a full column at ply 9
- `769d42b1-828d-4dc5-b81b-d11d1099b8a9` Back To Basics vs Savi5 beater 2.1: bad token at char 12
- `4f11a97a-9255-4402-8958-4622763e9c15` Savi5 beater 2.1 vs Back To Basics: column 12 outside the board at ply 18
- `cb4f1624-82ce-40b5-9258-ade1a7425f77` Back To Basics vs Savi5 beater 2.1: column 12 outside the board at ply 17
- `9bc80c89-9518-436d-9467-f93f5b341501` HereWeGo vs Savi5 beater 2.1: column 12 outside the board at ply 4
- `1e3c8208-dc35-4b21-a37a-47b6c102536a` HereWeGo vs Savi5 beater 2.1: column 12 outside the board at ply 6
- `3ee39cd2-9bbd-43ea-97b8-625ee6843685` Strange Quark 1 vs Savi5 beater 2.1: disc into a full column at ply 13
- `207029be-4c59-4fd7-8bf6-323eef513aa1` Savi5 beater 2.1 vs Strange Quark 1: column 12 outside the board at ply 11
- `39889284-c7d7-4f20-a93c-b92f648fff91` Strange Quark 1 vs Savi5 beater 2.1: column 12 outside the board at ply 12
- `42d7dd60-9382-4fce-b806-a6795dfbe40b` Savi5 beater 2.1 vs specialmovedestroyer: column 12 outside the board at ply 5
- `247ea049-cb3e-466d-bf18-489ad4567604` specialmovedestroyer vs Savi5 beater 2.1: disc into a full column at ply 9
- `79119b45-4a2c-4777-8b4f-26c78310f798` Savi5 beater 2.1 vs specialmovedestroyer: disc into a full column at ply 9
- `cf59b31d-15aa-4066-9be0-8f99fdec3491` specialmovedestroyer vs Savi5 beater 2.1: disc into a full column at ply 11
- `158cf5bd-b137-4897-919b-346fb7d548b5` Savi5 beater 2.1 vs specialmovedestroyer: disc into a full column at ply 9
- `e2e16e0e-6277-48f4-8dd3-c08a839ab55f` specialmovedestroyer vs Savi5 beater 2.1: disc into a full column at ply 11
- `05dbff50-6bad-4df9-98b2-f5e9c62825f8` Back To Basics vs Savi5 beater 2.1: column 12 outside the board at ply 19
- `65339aff-67df-4cd8-b7f9-3f82343b04a5` Savi5 beater 2.1 vs HereWeGo: disc into a full column at ply 16
- `cf059455-ef03-4f0d-a25f-5afc1d188ca7` Savi5 beater 2.1 vs HereWeGo: column 12 outside the board at ply 3
- `9571d467-5961-4327-a0e5-0074e07f4f90` HereWeGo vs Savi5 beater 2.1: disc into a full column at ply 10
- `dd4df832-5c87-4517-ab01-9c9822f70571` Savi5 beater 2.1 vs HereWeGo: disc into a full column at ply 16
- `96139820-65e5-4e74-b8f5-95ca6d9faa88` BeetaSaag vs specialmovedestroyer: disc into a full column at ply 17
- `cf6d3034-6af5-4663-a311-76b177f3454e` specialmovedestroyer vs BeetaSaag: disc into a full column at ply 17
- `f724548b-acd9-4048-bc5f-ecee020b5327` specialmovedestroyer vs Hurt me plenty: disc into a full column at ply 13
- `590b808d-e760-4315-b581-acf29c50813e` Hurt me plenty vs specialmovedestroyer: disc into a full column at ply 16
- `dd818d32-0b20-4a39-b3ef-179bb86567be` BeetaSaag vs HereWeGo: disc into a full column at ply 9
- `48502c59-91fb-43bf-b0b4-c45f0a688d4b` HereWeGo vs BeetaSaag: disc into a full column at ply 9
- `60ac6650-c073-4c5e-8185-29846749f2bf` BeetaSaag vs HereWeGo: disc into a full column at ply 9
- `57cf4428-8a95-41d2-8b1f-5b7b71700e71` HereWeGo vs BeetaSaag: disc into a full column at ply 10
- `c0cad3cb-debc-43ad-b1ce-f5740d25d656` HereWeGo vs Hurt me plenty: disc into a full column at ply 13
- `4f3ac84b-766c-456a-b7ff-6c90743bd1b1` Hurt me plenty vs HereWeGo: disc into a full column at ply 12
- `38c4133d-627a-42ff-b8cf-ddce7b22426e` HereWeGo vs Hurt me plenty: disc into a full column at ply 12
- `1c000f9e-3366-4b3d-8762-0a63ed291dfa` Hurt me plenty vs HereWeGo: disc into a full column at ply 12
- `d007441f-c09b-48d3-a992-cf34d4980b7a` HereWeGo vs Hurt me plenty: disc into a full column at ply 13
- `7831d5a3-d306-4e67-8a11-0a047c4ff7ba` Hurt me plenty vs HereWeGo: disc into a full column at ply 14
- `f43f119f-5446-43ae-991e-4c42fdbf4675` HereWeGo vs Savi5 beater 2.1: disc into a full column at ply 9
- `3c053876-4f14-44cf-8ea0-bfde7d0801f9` BeetaSaag vs lalalalabot: disc into a full column at ply 9
- `3f3d3fb5-a862-48ff-9cde-0eed5654389f` lalalalabot vs Savi5 beater 2.1: disc into a full column at ply 39
- `7916ed55-3ee1-4f9d-95ee-006ba4d58d20` lalalalabot vs Savi5 beater 2.1: disc into a full column at ply 18
- `f3f042b7-8cac-4b27-a827-437c7ac433cd` lalalalabot vs Savi5 beater 2.1: column 12 outside the board at ply 14
- `c3bc881b-ff3e-4a89-a697-dff2725d51e8` lalalalabot vs Savi5 beater 2.1: disc into a full column at ply 20
- `d9c03545-c4c4-4f2a-99de-1fca33673212` Savi5 beater 2.1 vs lalalalabot: column 12 outside the board at ply 5
- `eb2f7e89-ea1e-44e6-a4ae-140fe4c0952f` Savi5 beater 2.1 vs lalalalabot: column 12 outside the board at ply 5
- `3dd72b65-330b-4bbd-932f-41569bca399f` specialmovedestroyer vs lalalalabot: disc into a full column at ply 9
- `21109f78-5032-4f41-b10c-2b2dcd60fd88` Back To Basics vs lalalalabot: column 12 outside the board at ply 15
- `f6f043c3-b026-4b7b-81b8-c1be842f7044` HereWeGo vs lalalalabot: disc into a full column at ply 9
- `7541c7b3-41f5-4f6d-8817-6a6cfca51516` HereWeGo vs lalalalabot: disc into a full column at ply 9
- `259c9538-e514-4b98-bf0b-027efbde51ab` Savi6 - "Beat This" vs Savi5 beater 2.1: disc into a full column at ply 9
- `9e013bff-22be-4209-aeee-66116cfdeea5` Savi5 beater 2.1 vs Savi6 - "Beat This": column 12 outside the board at ply 5
- `3b0cd408-2e65-4147-8c9a-71500090821d` Savi6 - "Beat This" vs Savi5 beater 2.1: disc into a full column at ply 10
- `4d754152-b07e-40e9-838a-0f76be860461` Savi5 beater 2.1 vs Savi6 - "Beat This": column 12 outside the board at ply 5
- `709d2708-01f1-4d1b-82e1-5826d6d93da5` Savi6 - "Beat This" vs Savi5 beater 2.1: disc into a full column at ply 10
- `3821f03d-2d92-4d11-bebe-6e4531016d62` Savi5 beater 2.1 vs Savi6 - "Beat This": column 12 outside the board at ply 7
- `7250d96f-84a3-430a-9388-9e6250e6f29f` Savi6 - "Beat This" vs Savi5 beater 2.1: disc into a full column at ply 10
- `056aa25a-2cf0-49bf-8f79-10265b14bfdb` specialmovedestroyer vs Savi6 - "Beat This": disc into a full column at ply 9
- `bdec336c-b64f-41e0-b942-f6ba6abd01be` Savi6 - "Beat This" vs specialmovedestroyer: disc into a full column at ply 9
- `f3119afd-d948-4537-aa2b-5ac5c80485f3` Savi6 - "Beat This" vs lalalalabot: disc into a full column at ply 9
- `c19de05d-97ab-46f3-81b7-2b59852e325d` Hurt me plenty vs Savi6 - "Beat This": disc into a full column at ply 14
- `08ca405f-56d6-46ab-949f-c7c44c60dd51` Savi6 - "Beat This" vs Hurt me plenty: disc into a full column at ply 13
- `c3a9693e-2494-4183-a98b-7c86cc8b1db0` Savi7 - "The Prince" vs Savi5 beater 2.1: disc into a full column at ply 9
- `19c9026b-6f4a-4381-8a0a-598576c661cb` Savi7 - "The Prince" vs Savi5 beater 2.1: disc into a full column at ply 9
- `ae140b56-6573-4f45-91b3-f9cbdc802b56` Savi5 beater 2.1 vs Savi7 - "The Prince": column 12 outside the board at ply 5
- `0afc31d4-bd49-4864-baea-588fccec4e36` Savi7 - "The Prince" vs Savi5 beater 2.1: disc into a full column at ply 17
- `0eff6434-8158-419f-800e-3cf29fa0a10a` Savi5 beater 2.1 vs Savi7 - "The Prince": disc into a full column at ply 9
- `6106cc18-5dd5-4154-8701-4484b0ffb61b` Savi7 - "The Prince" vs BeetaSaag: column 12 outside the board at ply 5
- `37889ce5-b3e6-450a-a94b-9ad8de5d8dcb` Savi7 - "The Prince" vs BeetaSaag: disc into a full column at ply 11
- `86c12c8c-9b67-4f9b-9747-0fdbb8076216` BeetaSaag vs Savi7 - "The Prince": disc into a full column at ply 9
- `3fd0bb62-7d6b-4a80-8d6b-a1ee4ece0c9f` Savi7 - "The Prince" vs BeetaSaag: disc into a full column at ply 10
- `82238889-0b36-4129-8c0b-a07d84d42a15` BeetaSaag vs Savi7 - "The Prince": disc into a full column at ply 9
- `670fd852-73b5-43ec-aaf3-b655859c98ac` BeetaSaag vs Savi7 - "The Prince": disc into a full column at ply 9
- `94c7bee9-3d72-4259-9b85-9660a17859ec` Savi7 - "The Prince" vs Hurt me plenty: blast on a full column at ply 33
- `db7ae982-81c0-4244-82bf-dc9c7aa6f5ac` Testing_v2 vs Savi7 - "The Prince": column 12 outside the board at ply 4
- `f9be9a8c-bcf7-4e33-aca2-7164e0454920` Hurt me plenty vs Savi7 - "The Prince": disc into a full column at ply 37
- `c82e6f39-44da-40e5-983c-ec70568ba352` Savi7 - "The Prince" vs Hurt me plenty: blast on a full column at ply 29
- `0a3cc00d-3dc2-4c04-b009-013a4bf695b1` Hurt me plenty vs Savi7 - "The Prince": column 12 outside the board at ply 19
- `669bbdcd-f516-46cf-ad39-cb690ee00dcd` Savi7 - "The Prince" vs Hurt me plenty: blast on a full column at ply 28
- `119ad029-9686-4cdf-85a0-918bfe877567` Hurt me plenty vs Savi7 - "The Prince": blast on a full column at ply 34
- `1974a6a1-8e6e-4051-be04-4f7003650988` Savi7 - "The Prince" vs Hurt me plenty: column 12 outside the board at ply 20
- `2b07b84f-aa3d-4b93-8b82-dc451a16ca90` Savi7 - "The Prince" vs Testing_v2: column 15 outside the board at ply 2
- `6533d17d-2bdc-4481-bd67-9d202e332597` Savi7 - "The Prince" vs Testing_v2: column 12 outside the board at ply 6
- `84f236d6-d054-4617-b4cf-c3b52c821533` Testing_v2 vs Savi7 - "The Prince": column 12 outside the board at ply 4
- `cabcf34c-53a8-4eb9-8678-94305615552f` Savi7 - "The Prince" vs Testing_v2: column 12 outside the board at ply 5
- `468bad0b-44e5-4cbe-949f-8acb328fae6a` Savi5 beater 2.1 vs Savi7 - "The Prince": column 12 outside the board at ply 5
- `1a14c565-22a7-4241-8d4f-2d1853e6d38e` Savi7 - "The Prince" vs Savi5 beater 2.1: disc into a full column at ply 9
- `489e9e23-43e4-4b28-af16-fc1d35c46b3c` Savi7 - "The Prince" vs specialmovedestroyer: disc into a full column at ply 9
- `d42f1851-3a02-48b8-ac94-3348abd2fbd9` Savi7 - "The Prince" vs Strange Quark 1: column 14 outside the board at ply 3
- `360194be-c3ce-4114-99dd-c5a73bf7b8a4` Strange Quark 1 vs Savi7 - "The Prince": column 15 outside the board at ply 4
- `80e09adc-3c37-49de-ac4d-0318991a89a3` Savi7 - "The Prince" vs Strange Quark 1: column 15 outside the board at ply 3
- `f17b5cfa-5cf9-4ab6-9f9d-a4ace30272ee` Strange Quark 1 vs Savi7 - "The Prince": column 15 outside the board at ply 4
- `d4e2a519-852a-4084-96cf-1d0b37b959ae` Savi7 - "The Prince" vs Strange Quark 1: column 14 outside the board at ply 3
- `9d39f32c-d91c-47de-a21f-9c7f6f9bdefc` Strange Quark 1 vs Savi7 - "The Prince": column 15 outside the board at ply 4
- `e0bfa916-67df-4d0e-96b5-48fbd81a349a` Savi7 - "The Prince" vs Strange Quark 1: column 14 outside the board at ply 3
- `39a040af-b560-4aec-9a81-bc751916e558` specialmovedestroyer vs Savi7 - "The Prince": disc into a full column at ply 9
- `60b784cc-3c02-42e7-a049-dd37b09bb283` Savi7 - "The Prince" vs specialmovedestroyer: disc into a full column at ply 9
- `239539f6-1361-4e6d-ae9e-9f45e7401c23` specialmovedestroyer vs Savi7 - "The Prince": disc into a full column at ply 9
- `37030943-b6e3-4475-a2ca-3f64c2ee95fd` Savi7 - "The Prince" vs Back To Basics: column 12 outside the board at ply 7
- `308b7dcb-17dc-4f87-88d0-cc33093fc3f0` Back To Basics vs Savi7 - "The Prince": column 14 outside the board at ply 6
- `c7bd3eba-4f90-4898-89c0-3d0543a5218d` Savi7 - "The Prince" vs lalalalabot: disc into a full column at ply 9
- `7b386928-5f35-4ec1-9b43-2281b41fe045` specialmovedestroyer vs Savi7 - "The Prince": disc into a full column at ply 9
- `e8fee4da-ff06-4d96-a888-43e46e718713` Savi7 - "The Prince" vs specialmovedestroyer: disc into a full column at ply 9
- `67cff3e2-cada-4288-830d-37f1e910adb4` Savi7 - "The Prince" vs specialmovedestroyer: disc into a full column at ply 9
- `a47817e6-40d6-4516-bb67-475bedbcb466` Savi7 - "The Prince" vs Back To Basics: column 15 outside the board at ply 3
- `958272fd-e4ec-49e0-a5d5-292099445852` Back To Basics vs Savi7 - "The Prince": column 12 outside the board at ply 8
- `0e95a526-7d15-44bb-a7e8-66823ad5de56` Savi7 - "The Prince" vs Back To Basics: column 14 outside the board at ply 3
- `ae707a0d-1897-4766-9ad3-3be64f217aef` Back To Basics vs Savi7 - "The Prince": column 12 outside the board at ply 8
- `6e0febe0-d317-463c-ac44-e16dbdcc4036` Savi7 - "The Prince" vs lalalalabot: disc into a full column at ply 9
- `1db63ef0-292b-4e91-a2c1-c497d17cadc7` lalalalabot vs Savi7 - "The Prince": disc into a full column at ply 13
- `5c0572cf-ed4b-4602-862c-0ed07ec2087c` lalalalabot vs Savi7 - "The Prince": disc into a full column at ply 48
- `8c3a7b0e-7c39-49f1-982d-1d0a71f9f103` Savi7 - "The Prince" vs lalalalabot: disc into a full column at ply 9
- `55123ef8-d42d-40e3-88a6-79ec15b0f96b` lalalalabot vs Savi7 - "The Prince": disc into a full column at ply 37
- `dff4dc65-38ff-4214-8341-07e4f9b37b97` Savi7 - "The Prince" vs lalalalabot: disc into a full column at ply 9
- `*` after a token marks a blast in that column: 384 blasts in 181 games; smallest cooldown counter at a blast 10; earliest blast is the own turn #10 (0-based, own turns counted from 0); smallest gap between two blasts of one side 10 own turns. Replaying with skipped turns NOT counting toward the cooldown gives 413 of 550 consistent, so the data cannot tell the two apart.
- A skipped turn (timeout or full column) leaves no token; replay finds 356 of them as breaks in the F/S alternation. In 508 of 550 games the breaks equal the logged timeout / column-full lines in count and sides (a break is invisible when both sides skip in a row).

## Per bot

Decided by: games of this bot ending in a normal six / in a blast (either side winning). Blasts used counts every `*` token by this bot. Length is plies per game.

| Bot | Games | W/L | As F W/L | As S W/L | Six / blast games | Its wins six / blast | Blasts used | Mean plies | Median plies |
|---|---|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | 145 | 140/5 | 65/3 | 75/2 | 133 / 12 | 128 / 12 | 76 | 29.4 | 18.0 |
| Testimine-M7 | 91 | 2/89 | 2/38 | 0/51 | 91 / 0 | 2 / 0 | 35 | 23.9 | 19.0 |
| Testing_v2 | 90 | 45/45 | 24/20 | 21/25 | 85 / 5 | 42 / 3 | 70 | 39.6 | 25.0 |
| Hurt me plenty | 80 | 29/51 | 14/24 | 15/27 | 78 / 2 | 29 / 0 | 0 | 23.2 | 23.0 |
| BeetaSaag | 77 | 32/45 | 17/19 | 15/26 | 75 / 2 | 32 / 0 | 0 | 22.9 | 12.0 |
| Strange Quark 1 | 77 | 15/62 | 10/28 | 5/34 | 77 / 0 | 15 / 0 | 0 | 14.5 | 14.0 |
| specialmovedestroyer | 64 | 29/35 | 15/18 | 14/17 | 63 / 1 | 29 / 0 | 20 | 24.0 | 12.0 |
| Savi5 | 58 | 53/5 | 27/3 | 26/2 | 58 / 0 | 53 / 0 | 63 | 51.8 | 38.5 |
| Savi6 - "Beat this" | 57 | 55/2 | 30/0 | 25/2 | 56 / 1 | 54 / 1 | 47 | 53.2 | 44.0 |
| Savi7 - "The Prince" | 52 | 45/7 | 26/3 | 19/4 | 52 / 0 | 45 / 0 | 2 | 0.0 | 0.0 |
| HereWeGo | 48 | 13/35 | 10/18 | 3/17 | 48 / 0 | 13 / 0 | 0 | 6.6 | 9.0 |
| AntiStrat_FINAL | 36 | 10/26 | 6/11 | 4/15 | 36 / 0 | 10 / 0 | 34 | 28.9 | 24.0 |
| \u006c\u0061\u006c\2 | 36 | 17/19 | 10/8 | 7/11 | 35 / 1 | 17 / 0 | 0 | 40.6 | 19.0 |
| lalalabot | 34 | 14/20 | 9/10 | 5/10 | 32 / 2 | 14 / 0 | 5 | 35.9 | 19.5 |
| good night | 33 | 11/22 | 7/11 | 4/11 | 27 / 6 | 11 / 0 | 14 | 40.2 | 33.0 |
| lalalalabot | 33 | 19/14 | 11/6 | 8/8 | 33 / 0 | 19 / 0 | 0 | 5.5 | 0.0 |
| sleep tight | 33 | 9/24 | 8/10 | 1/14 | 33 / 0 | 9 / 0 | 18 | 22.9 | 18.0 |
| Back To Basics | 31 | 5/26 | 4/12 | 1/14 | 31 / 0 | 5 / 0 | 0 | 7.5 | 11.0 |
| Savi6 - "Beat This" | 25 | 7/18 | 5/8 | 2/10 | 25 / 0 | 7 / 0 | 0 | 5.5 | 6.0 |

## Game length

All games: plies min/median/p90/max = 0/16/67/136, mean 26.3; turns (skips included) 0/17/69/136. Games ending by a blast: 16 of 550 (2.9%). Games over 200/300/400 plies: 0/0/0 (over 200/300/400 turns: 0/0/0).

Plies in 20-ply buckets (all games):

| Plies | Games |  |
|---|---|---|
| 1-20 | 317 | ############################################### |
| 21-40 | 105 | ################ |
| 41-60 | 66 | ########## |
| 61-80 | 22 | #### |
| 81-100 | 19 | ### |
| 101-120 | 12 | ## |
| 121-140 | 9 | ## |

Per bot (plies; the over-N columns count games with more than N plies):

| Bot | Games | min/median/p90/max | > 200 | > 300 | > 400 |
|---|---|---|---|---|---|
| Savi5 beater 2.1 | 145 | 0/18/94/136 | 0 | 0 | 0 |
| Testimine-M7 | 91 | 11/19/46/71 | 0 | 0 | 0 |
| Testing_v2 | 90 | 0/25/112/130 | 0 | 0 | 0 |
| Hurt me plenty | 80 | 0/23/46/93 | 0 | 0 | 0 |
| BeetaSaag | 77 | 0/12/51/96 | 0 | 0 | 0 |
| Strange Quark 1 | 77 | 0/14/31/36 | 0 | 0 | 0 |
| specialmovedestroyer | 64 | 0/12/64/96 | 0 | 0 | 0 |
| Savi5 | 58 | 13/38.5/123/136 | 0 | 0 | 0 |
| Savi6 - "Beat this" | 57 | 15/44/113/128 | 0 | 0 | 0 |
| Savi7 - "The Prince" | 52 | 0/0/0/0 | 0 | 0 | 0 |
| HereWeGo | 48 | 0/9/13/14 | 0 | 0 | 0 |
| AntiStrat_FINAL | 36 | 11/24/53/67 | 0 | 0 | 0 |
| \u006c\u0061\u006c\2 | 36 | 11/19/100/112 | 0 | 0 | 0 |
| lalalabot | 34 | 11/19.5/93/112 | 0 | 0 | 0 |
| good night | 33 | 11/33/74/96 | 0 | 0 | 0 |
| lalalalabot | 33 | 0/0/14/33 | 0 | 0 | 0 |
| sleep tight | 33 | 11/18/35/71 | 0 | 0 | 0 |
| Back To Basics | 31 | 0/11/13/18 | 0 | 0 | 0 |
| Savi6 - "Beat This" | 25 | 0/6/11/14 | 0 | 0 | 0 |

Our team (No beaches?): 145 games, plies min/median/p90/max = 0/18/94/136, over 200/300/400 plies: 0/0/0.

## First-player advantage

First player (F) won 300 of 550 (54.5%, normal-approximation 95% interval 50.4-58.7%). By a six: F 294, S 240; by a blast: F 6, S 10. In games without any timeout or full-column event: F 235 of 421 (55.8%). Bots differ in strength, so this mixes colour with pairing; the pairings are colour-balanced only if each ordered pair is played equally often.

## Head to head

Cell = wins of the row bot over the column bot, as wins-losses (all colours).

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Savi5 beater 2.1 | . | 7-0 | 10-0 | 13-0 | 14-0 | 14-0 | 14-0 | 4-1 | 1-2 | 5-2 | 7-0 | 7-0 | 6-0 | 7-0 | 7-0 | 6-0 | 6-0 | 5-0 | 7-0 |
| 2 | Testimine-M7 | 0-7 | . | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 |  |  | 0-7 | 0-7 | 0-7 | 0-7 |  | 2-5 |  |  |
| 3 | Testing_v2 | 0-10 | 7-0 | . | 4-4 | 4-2 | 3-2 | 5-2 | 1-4 | 1-4 | 0-5 | 0-5 | 5-0 | 4-0 | 3-0 | 5-0 | 0-3 | 3-0 | 0-2 | 0-2 |
| 4 | Hurt me plenty | 0-13 | 7-0 | 4-4 | . | 1-3 | 4-0 | 0-3 | 0-7 | 0-7 | 0-7 | 6-0 | 2-0 | 0-2 | 0-2 |  | 0-2 | 1-1 | 2-0 | 2-0 |
| 5 | BeetaSaag | 0-14 | 7-0 | 2-4 | 3-1 | . | 2-0 | 1-2 | 0-7 | 0-7 | 0-6 | 6-0 | 2-0 | 1-1 | 1-1 | 1-0 | 1-1 | 1-1 | 2-0 | 2-0 |
| 6 | Strange Quark 1 | 0-14 | 7-0 | 2-3 | 0-4 | 0-2 | . | 0-4 | 0-6 | 0-7 | 0-7 | 3-2 | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | 1-1 | 1-1 | 1-1 |
| 7 | specialmovedestroyer | 0-14 | 7-0 | 2-5 | 3-0 | 2-1 | 4-0 | . |  | 0-7 | 1-6 | 4-0 |  |  | 1-1 |  | 1-1 |  | 2-0 | 2-0 |
| 8 | Savi5 | 1-4 | 7-0 | 4-1 | 7-0 | 7-0 | 6-0 |  | . |  |  |  | 7-0 | 7-0 |  |  |  | 7-0 |  |  |
| 9 | Savi6 - "Beat this" | 2-1 | 7-0 | 4-1 | 7-0 | 7-0 | 7-0 | 7-0 |  | . |  |  |  |  | 7-0 | 7-0 |  |  |  |  |
| 10 | Savi7 - "The Prince" | 2-5 |  | 5-0 | 7-0 | 6-0 | 7-0 | 6-1 |  |  | . |  |  |  |  |  | 6-1 |  | 6-0 |  |
| 11 | HereWeGo | 0-7 |  | 5-0 | 0-6 | 0-6 | 2-3 | 0-4 |  |  |  | . |  |  |  |  | 0-5 |  | 5-1 | 1-3 |
| 12 | AntiStrat_FINAL | 0-7 | 7-0 | 0-5 | 0-2 | 0-2 | 2-0 |  | 0-7 |  |  |  | . | 0-2 |  |  |  | 1-1 |  |  |
| 13 | \u006c\u0061\u006c\2 | 0-6 | 7-0 | 0-4 | 2-0 | 1-1 | 2-0 |  | 0-7 |  |  |  | 2-0 | . |  | 1-1 |  | 2-0 |  |  |
| 14 | lalalabot | 0-7 | 7-0 | 0-3 | 2-0 | 1-1 | 2-0 | 1-1 |  | 0-7 |  |  |  |  | . | 1-1 |  |  |  |  |
| 15 | good night | 0-7 | 7-0 | 0-5 |  | 0-1 | 2-0 |  |  | 0-7 |  |  |  | 1-1 | 1-1 | . |  |  |  |  |
| 16 | lalalalabot | 0-6 |  | 3-0 | 2-0 | 1-1 | 2-0 | 1-1 |  |  | 1-6 | 5-0 |  |  |  |  | . |  | 2-0 | 2-0 |
| 17 | sleep tight | 0-6 | 5-2 | 0-3 | 1-1 | 1-1 | 1-1 |  | 0-7 |  |  |  | 1-1 | 0-2 |  |  |  | . |  |  |
| 18 | Back To Basics | 0-5 |  | 2-0 | 0-2 | 0-2 | 1-1 | 0-2 |  |  | 0-6 | 1-5 |  |  |  |  | 0-2 |  | . | 1-1 |
| 19 | Savi6 - "Beat This" | 0-7 |  | 2-0 | 0-2 | 0-2 | 1-1 | 0-2 |  |  |  | 3-1 |  |  |  |  | 0-2 |  | 1-1 | . |

## Our team: No beaches?

145 games, 140 wins, 5 losses. Opponent column shows our bot's version in brackets; blasts are ours/theirs; our skipped turns are numbered by turn (skipped turns included, 1-based) and, when the log pairs up, their cause; the last column is the OOM line of the game, if any (not tagged with a bot).

| # | Opponent (our version) | Side | Res | Plies | Turns | Decided by | Blasts | Our skipped turns | Opp skips | OOM line |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testing_v2 (Savi5 beater 2.1) | S | W | 99 | 100 | blast | 3/3 | - | 1 | 312 MB |
| 2 | Testing_v2 (Savi5 beater 2.1) | F | W | 117 | 119 | six | 3/4 | - | 2 | 305 MB |
| 3 | Testing_v2 (Savi5 beater 2.1) | S | W | 84 | 88 | six | 0/1 | - | 4 | 309 MB |
| 4 | Hurt me plenty (Savi5 beater 2.1) | F | W | 34 | 37 | six | 0/0 | turn 17 (timeout) | 2 | 311 MB |
| 5 | AntiStrat_FINAL (Savi5 beater 2.1) | S | W | 58 | 58 | six | 1/2 | - | 0 | - |
| 6 | AntiStrat_FINAL (Savi5 beater 2.1) | S | W | 46 | 46 | six | 1/2 | - | 0 | - |
| 7 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 29 | 29 | six | 1/1 | - | 0 | - |
| 8 | Savi5 (Savi5 beater 2.1) | S | W | 104 | 104 | six | 1/2 | - | 0 | - |
| 9 | Savi5 (Savi5 beater 2.1) | S | W | 128 | 128 | six | 2/4 | - | 0 | 201 MB |
| 10 | Savi5 (Savi5 beater 2.1) | S | W | 132 | 132 | six | 3/4 | - | 0 | 200 MB |
| 11 | Savi5 (Savi5 beater 2.1) | F | L | 136 | 136 | six | 3/4 | - | 0 | 200 MB |
| 12 | BeetaSaag (Savi5 beater 2.1) | F | W | 31 | 31 | six | 0/0 | - | 0 | 201 MB |
| 13 | BeetaSaag (Savi5 beater 2.1) | S | W | 48 | 48 | six | 2/0 | - | 0 | 208 MB |
| 14 | BeetaSaag (Savi5 beater 2.1) | F | W | 63 | 63 | six | 2/0 | - | 0 | 200 MB |
| 15 | BeetaSaag (Savi5 beater 2.1) | S | W | 50 | 50 | six | 1/0 | - | 0 | - |
| 16 | BeetaSaag (Savi5 beater 2.1) | S | W | 26 | 26 | blast | 1/0 | - | 0 | - |
| 17 | BeetaSaag (Savi5 beater 2.1) | F | W | 37 | 37 | six | 1/0 | - | 0 | 209 MB |
| 18 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 34 | 34 | six | 1/0 | - | 0 | - |
| 19 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 17 | 17 | six | 0/0 | - | 0 | 209 MB |
| 20 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | 230 MB |
| 21 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 17 | 17 | six | 0/0 | - | 0 | 206 MB |
| 22 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 223 MB |
| 23 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 15 | 15 | six | 0/0 | - | 0 | 205 MB |
| 24 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | 213 MB |
| 25 | Testing_v2 (Savi5 beater 2.1) | S | W | 129 | 132 | blast | 4/3 | - | 3 | 285 MB |
| 26 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 215 MB |
| 27 | sleep tight (Savi5 beater 2.1) | S | W | 16 | 16 | six | 0/0 | - | 0 | 294 MB |
| 28 | sleep tight (Savi5 beater 2.1) | F | W | 15 | 15 | six | 0/0 | - | 0 | 263 MB |
| 29 | sleep tight (Savi5 beater 2.1) | F | W | 27 | 27 | six | 0/1 | - | 0 | 255 MB |
| 30 | sleep tight (Savi5 beater 2.1) | S | W | 30 | 30 | six | 0/1 | - | 0 | 292 MB |
| 31 | sleep tight (Savi5 beater 2.1) | F | W | 13 | 13 | six | 0/0 | - | 0 | 255 MB |
| 32 | sleep tight (Savi5 beater 2.1) | S | W | 28 | 28 | six | 1/1 | - | 0 | 285 MB |
| 33 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 43 | 43 | blast | 1/0 | - | 0 | 249 MB |
| 34 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 83 | 83 | six | 1/0 | - | 0 | 247 MB |
| 35 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 99 | 99 | six | 2/0 | - | 0 | 269 MB |
| 36 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 261 MB |
| 37 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 93 | 93 | six | 3/0 | - | 0 | 216 MB |
| 38 | Hurt me plenty (Savi5 beater 2.1) | F | W | 31 | 31 | blast | 1/0 | - | 0 | 294 MB |
| 39 | Hurt me plenty (Savi5 beater 2.1) | S | W | 45 | 48 | six | 0/0 | - | 3 | 331 MB |
| 40 | Hurt me plenty (Savi5 beater 2.1) | F | W | 34 | 35 | six | 0/0 | - | 1 | 297 MB |
| 41 | Hurt me plenty (Savi5 beater 2.1) | S | W | 32 | 32 | six | 0/0 | - | 0 | 335 MB |
| 42 | Hurt me plenty (Savi5 beater 2.1) | S | W | 46 | 48 | six | 0/0 | - | 2 | 336 MB |
| 43 | Testimine-M7 (Savi5 beater 2.1) | F | W | 13 | 13 | six | 0/0 | - | 0 | - |
| 44 | Testimine-M7 (Savi5 beater 2.1) | S | W | 28 | 28 | six | 0/1 | - | 0 | - |
| 45 | Testimine-M7 (Savi5 beater 2.1) | F | W | 35 | 35 | six | 0/1 | - | 0 | - |
| 46 | Testimine-M7 (Savi5 beater 2.1) | S | W | 48 | 48 | six | 1/1 | - | 0 | - |
| 47 | Testimine-M7 (Savi5 beater 2.1) | F | W | 27 | 27 | six | 0/0 | - | 0 | - |
| 48 | Testimine-M7 (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | - |
| 49 | Testimine-M7 (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | - |
| 50 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 33 | 33 | six | 1/1 | - | 0 | - |
| 51 | AntiStrat_FINAL (Savi5 beater 2.1) | S | W | 44 | 44 | six | 1/2 | - | 0 | - |
| 52 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 23 | 23 | six | 0/1 | - | 0 | - |
| 53 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 67 | 67 | six | 2/3 | - | 0 | - |
| 54 | Savi5 (Savi5 beater 2.1) | F | W | 105 | 105 | six | 1/3 | - | 0 | 200 MB |
| 55 | BeetaSaag (Savi5 beater 2.1) | F | W | 45 | 45 | six | 1/0 | - | 0 | 206 MB |
| 56 | good night (Savi5 beater 2.1) | S | W | 63 | 64 | six | 2/1 | turn 28 (timeout) | 0 | 306 MB |
| 57 | good night (Savi5 beater 2.1) | F | W | 29 | 29 | blast | 1/0 | - | 0 | - |
| 58 | good night (Savi5 beater 2.1) | S | W | 72 | 72 | six | 1/1 | - | 0 | - |
| 59 | good night (Savi5 beater 2.1) | F | W | 29 | 29 | blast | 1/0 | - | 0 | - |
| 60 | good night (Savi5 beater 2.1) | S | W | 72 | 72 | six | 1/1 | - | 0 | - |
| 61 | good night (Savi5 beater 2.1) | F | W | 27 | 27 | blast | 1/0 | - | 0 | - |
| 62 | good night (Savi5 beater 2.1) | S | W | 94 | 94 | six | 1/1 | - | 0 | - |
| 63 | lalalabot (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 200 MB |
| 64 | lalalabot (Savi5 beater 2.1) | F | W | 29 | 29 | six | 0/1 | - | 0 | 230 MB |
| 65 | lalalabot (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 205 MB |
| 66 | lalalabot (Savi5 beater 2.1) | F | W | 95 | 95 | six | 1/1 | - | 0 | 235 MB |
| 67 | lalalabot (Savi5 beater 2.1) | S | W | 77 | 78 | blast | 1/0 | turn 20 (timeout) | 0 | 235 MB |
| 68 | lalalabot (Savi5 beater 2.1) | F | W | 69 | 69 | six | 2/0 | - | 0 | 225 MB |
| 69 | lalalabot (Savi5 beater 2.1) | S | W | 94 | 94 | blast | 3/1 | - | 0 | 278 MB |
| 70 | Savi6 - "Beat this" (Savi5 beater 2.1) | S | L | 115 | 115 | six | 2/1 | - | 0 | 200 MB |
| 71 | Savi6 - "Beat this" (Savi5 beater 2.1) | F | W | 109 | 109 | six | 1/1 | - | 0 | 205 MB |
| 72 | Savi6 - "Beat this" (Savi5 beater 2.1) | F | L | 122 | 122 | six | 2/4 | - | 0 | 244 MB |
| 73 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 48 | 48 | six | 1/1 | - | 0 | - |
| 74 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 56 | 56 | six | 1/1 | - | 0 | 201 MB |
| 75 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 43 | 43 | six | 0/1 | - | 0 | - |
| 76 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 55 | 55 | blast | 2/1 | - | 0 | 201 MB |
| 77 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 84 | 84 | six | 2/1 | - | 0 | 223 MB |
| 78 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 41 | 41 | six | 0/1 | - | 0 | - |
| 79 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 78 | 78 | six | 1/1 | - | 0 | 203 MB |
| 80 | Hurt me plenty (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 81 | BeetaSaag (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 201 MB |
| 82 | BeetaSaag (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 207 MB |
| 83 | BeetaSaag (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 227 MB |
| 84 | BeetaSaag (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 200 MB |
| 85 | BeetaSaag (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 230 MB |
| 86 | Testing_v2 (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 3 | 237 MB |
| 87 | Testing_v2 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 2 | 201 MB |
| 88 | Testing_v2 (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 6 | 220 MB |
| 89 | Testing_v2 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 2 | 253 MB |
| 90 | Testing_v2 (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 1 | 211 MB |
| 91 | Testing_v2 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 4 | 247 MB |
| 92 | Hurt me plenty (Savi5 beater 2.1) | F | W | 0 | 0 | none | 1/0 | turn 1, turn 3, turn 17, turn 27, turn 33, turn 35, turn 37 | 1 | 200 MB |
| 93 | Hurt me plenty (Savi5 beater 2.1) | S | W | 17 | 22 | blast | 1/0 | turn 2, turn 6, turn 16, turn 18, turn 20 | 0 | - |
| 94 | Hurt me plenty (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 201 MB |
| 95 | Hurt me plenty (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 96 | Hurt me plenty (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 97 | Hurt me plenty (Savi5 beater 2.1) | S | W | 23 | 24 | six | 0/0 | - | 1 | 205 MB |
| 98 | BeetaSaag (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 99 | BeetaSaag (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 200 MB |
| 100 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 238 MB |
| 101 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 268 MB |
| 102 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 220 MB |
| 103 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 251 MB |
| 104 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 278 MB |
| 105 | Back To Basics (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | turn 2 (timeout) | 0 | 324 MB |
| 106 | Back To Basics (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 270 MB |
| 107 | Back To Basics (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 303 MB |
| 108 | Back To Basics (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 355 MB |
| 109 | HereWeGo (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 302 MB |
| 110 | HereWeGo (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 288 MB |
| 111 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 290 MB |
| 112 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 284 MB |
| 113 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 317 MB |
| 114 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 282 MB |
| 115 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 314 MB |
| 116 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 249 MB |
| 117 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 283 MB |
| 118 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 331 MB |
| 119 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 331 MB |
| 120 | Back To Basics (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 290 MB |
| 121 | HereWeGo (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 340 MB |
| 122 | HereWeGo (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 123 | HereWeGo (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 124 | HereWeGo (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 125 | HereWeGo (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 126 | lalalalabot (Savi5 beater 2.1) | S | W | 0 | 0 | none | 1/0 | turn 14 (timeout) | 0 | - |
| 127 | lalalalabot (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 128 | lalalalabot (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 129 | lalalalabot (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 130 | lalalalabot (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 131 | lalalalabot (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 132 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | turn 2 | 0 | 205 MB |
| 133 | Savi6 - "Beat This" (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 134 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 205 MB |
| 135 | Savi6 - "Beat This" (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | - |
| 136 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 208 MB |
| 137 | Savi6 - "Beat This" (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 230 MB |
| 138 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 218 MB |
| 139 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 200 MB |
| 140 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 426 MB |
| 141 | Savi7 - "The Prince" (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 435 MB |
| 142 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | L | 0 | 0 | none | 0/0 | - | 0 | 441 MB |
| 143 | Savi7 - "The Prince" (Savi5 beater 2.1) | F | L | 0 | 0 | none | 0/0 | - | 0 | 447 MB |
| 144 | Savi7 - "The Prince" (Savi5 beater 2.1) | F | W | 0 | 0 | none | 0/0 | - | 0 | 201 MB |
| 145 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | W | 0 | 0 | none | 0/0 | - | 0 | 200 MB |

Our own timeout lines and the OOM line of the same game:

- Game #4 vs Hurt me plenty: our timeout line at turn 17; OOM line 311 MB, 696 ms before the timeout.
- Game #56 vs good night: our timeout line at turn 28; OOM line 306 MB, 747 ms before the timeout.
- Game #67 vs lalalabot: our timeout line at turn 20; OOM line 235 MB, 495 ms before the timeout.
- Game #80 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #82 vs BeetaSaag: our timeout line; OOM line 207 MB, 358 ms after the timeout.
- Game #87 vs Testing_v2: our timeout line; OOM line 201 MB, 1480 ms before the timeout.
- Game #89 vs Testing_v2: our timeout line; OOM line 253 MB, 2076 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 8104 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 8001 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 7800 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 7403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 7302 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6999 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6803 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6618 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6602 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6500 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6400 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6399 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6196 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5804 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5598 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5502 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5204 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5100 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5004 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4900 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4700 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4502 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4299 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4000 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3700 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3618 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3404 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3303 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3204 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2904 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2799 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2503 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2402 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2303 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2200 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2102 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2003 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1803 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1617 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1302 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1201 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1199 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1001 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 904 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 702 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 618 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 400 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 203 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 18 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 296 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 300 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 398 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 500 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 599 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 801 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1096 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1098 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1383 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1597 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1782 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1897 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1999 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2196 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2197 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2498 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2696 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2797 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2904 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3098 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3198 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3300 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3582 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3800 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3897 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3998 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4197 ms before the timeout.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #105 vs Back To Basics: our timeout line at turn 2; OOM line 324 MB, 1397 ms before the timeout.
- Game #126 vs lalalalabot: our timeout line at turn 14; OOM line absent.
- Game #132 vs Savi6 - "Beat This": our timeout line; OOM line 205 MB, 1113 ms before the timeout.
- Game #132 vs Savi6 - "Beat This": our timeout line; OOM line 205 MB, 1521 ms before the timeout.
- Game #143 vs Savi7 - "The Prince": our timeout line; OOM line 447 MB, 1222 ms before the timeout.
- Game #144 vs Savi7 - "The Prince": our timeout line; OOM line 201 MB, 2453 ms before the timeout.

### Loss: Savi5 beater 2.1 (F) vs Savi5 (S)

Match id `e9582887-0ab6-4476-9d51-79d16f87415b`. Decided at ply 136 (turn 136, last token `S7`, by a six, win_at 135). Skipped turns: none.

Moves:

```
F3S5F5S5F5S5F5S5F3S3F3S3F3S3F4S4F4S4F5S3F4S4F4S7F4S8F7S1F8S1FaSaF8S7*F1S1F7S7F8S8F7S8F7S7F8S7F1S1F8S1F7SaF2S2F2S2F8SaF8SaFaS1FaSaF6S2*F3S2F3S2F3S6F2S6F9*S9F6S6F6SaFaS9F8S8FbS0*FbSbFbS0F0S0F0S0F0S1F1S0F1S0F1S2F2S1F9S9F9S9F9*S8*F7S8FaS8FaSaF9SbFbSbF9SbF9S9F8S8F7S7F6*S6F5S6F5S5F6S7
```

Position after the deciding move (top row first, F = X, S = O):

```
OOXXXOX.OOOO
OXOXXXO.XXXO
XXXXOXOOOXXX
OXOOXOOXOXXO
XOOOOXXXXOXX
OOXOXOOOXXOO
XOOXOXOXOOOX
OXXXXOXOOOOX
0123456789ab
```

### Loss: Savi5 beater 2.1 (S) vs Savi6 - "Beat this" (F)

Match id `01c00039-a661-496c-85b3-12bda7c2d613`. Decided at ply 115 (turn 115, last token `Fa`, by a six, win_at 114). Skipped turns: none.

Moves:

```
F5S6F6S6F6S6F6S6F3S6F5S5F5S5F5S5F5S3F3S3F3S3F3S3FbS2F9S2F2S2F7SbF7S7F9S9F9S7F7S7F9S7FbS9F9S7F9S2F2S2F0S2F0S0FbS0FbS0FbSbF4S4F4SbF0S4F1S4F1S4F1S1F4S0F4S0Fa*S9F8S8F9SbFbS8F8S8*F7S7F9S9F9S7F1SaF8S8F8S8F8S1FaS1FaSaF1Sa*F9SbF9SbFaSbF9SaFa
```

Position after the deciding move (top row first, F = X, S = O):

```
OXOOXXOOXX.O
OOOXXOOOOX.O
XOXOOXXXXXXO
OXOXOOOOOXOX
OOOOOXXOXOXO
OXXXXOOOOXXX
XXOOOXXXOXXX
XXOXXXOXXOOX
0123456789ab
```

### Loss: Savi5 beater 2.1 (F) vs Savi6 - "Beat this" (S)

Match id `440af18b-3b2c-4b5d-8365-18a8e9cd29f6`. Decided at ply 122 (turn 122, last token `S6`, by a six, win_at 121). Skipped turns: none.

Moves:

```
F3S5F5S5F5S5F5S5F5S3F3S3F3S3F3S3F8S8F8S8F8S8F4S4F4S8F4SbF8S9F7S9F9S4F7S7F4S4*F4S7F5S7F7S3F9S5F5S7F6S4F6S7F3S9F3S9F9Sa*FbSaF4SaF2S2F2*S1F6SaFaSaF0SaF2SaFaS3F3S3F2S2F2S1*F6SbF2S2F2S1F2S0F2S2F9S9F9S0FbS0F1S1F0S1FbSbFbSbFbS0*F0S0F0S0F1S0F1S1F1*S1F2S6F6S6
```

Position after the deciding move (top row first, F = X, S = O):

```
...OXX.OXXXX
...XOOOOOOOO
.OXOXXXXOXOX
OXXXOOOOXXOO
XXOXXXXOOOXX
OOXOXOXOXOOX
OXXXOXXXOXOO
OOOXXOXXXXOX
0123456789ab
```

### Loss: Savi5 beater 2.1 (S) vs Savi7 - "The Prince" (F)

Match id `0afc31d4-bd49-4864-baea-588fccec4e36`. Decided at ply 0 (turn 0, last token `Fa`, by a none, win_at 168). Skipped turns: none.

Moves:

```
FaSbFaSaFbSbFaSaFaSaFaSbFbSbFbSbFaSbFaSaFbSbF5S9*Fa*SaFbS8FcS6FbS7F9S9F9S9F8S8F7S9FaSbFaSaF5S4*F9S9F8*S8FbS9F4SbFaSaFbSfFfSfFfSfF9S9FfScFfScFc*Sb*F9ScF9S9FbS9F9SbFaS9FfSfF4S6F6SaFbS9FbS3*Fc*SbFfS6F6S6F6S6F4SbFfSaFbSfF4ScF6S4FcScFcSc*Fc*S4F4S6FcScFcScFcS6F6S6F4ScFcScFbScF8S8FdS4Fd*Sd*FcS4FdSdFdScF4S4FcScFcS8F4S4FdSdFaSeFfScFfS6FfS4F4S8F8S3*FdSdFd*ScFd
```

Position after the deciding move (top row first, F = X, S = O):

```
..........XO
..........OX
..........XO
..........OX
..........XO
..........OO
..........XX
..........XO
0123456789ab
```

### Loss: Savi5 beater 2.1 (F) vs Savi7 - "The Prince" (S)

Match id `0eff6434-8158-419f-800e-3cf29fa0a10a`. Decided at ply 0 (turn 0, last token `Fa`, by a none, win_at 340). Skipped turns: none.

Moves:

```
FaSaFaSaFaSaFaSaFaSaFaSbFaSbFbSbFbSbFbSbSbFbSgFbSb*Fc*SbF9S8FbSeFaSbF9S9FbS9F9S9F9SaF9SaF9S9*F9*S9F9S9F9S9FeS9FfSbFfSfFgSfFfSfFfSgFgSgFgSgFgSfFgSeFeSgFeSe*Ff*SfFfSfFgSfFeSgFfSgFgSfFfSfFiSbFkSfFaS9F7*S8FkSgFaSaFiSeFeSh*FkSgFgSgFcSkFcSbFcSeFeSeFe*SfFcSeFcScFfSd*FcSeFcScFcScFcSeFcSeFeScFcS9F8*ScF9SeFeS8*FfSgFcS5F5S5F8S8F4SfF8S8F8S8F8*S6F6S9F8S8F8S8*F8S9F8S8FaSaFeS9F9S9F4S5F4S5F5S5F5S4F8S7*F8S8F4*S5F8S4F5S5F9S8F3SaFaS9FfSgF3ShFhSh*FgS5Fh*SgFgSkFjSkFkSkFgSkFkSkFkS5F5ShFgS5*F5S5Fh*SgF5S4F4S4F4S4F9S4F4S4F5S8F4SgF4S5F4SaF9S8FkS7F7S6F6S7*F6S8F8S7F3*SjFkSjFjSjFgSjFgS7F4S4FjS7F7SjFkS7F7S6*F6*S6FjSjFjS6F3S2FjSjFlS4FlSlF3S7F5S5F3SdF8S5F5S5F5S6F6S6F3S3Fh*S6F6S6FjS6FgShFgSjF6S6F6S6FhS6F6ShFhSiF3*S3F4ShF3SiFhSiFiSd*
```

Position after the deciding move (top row first, F = X, S = O):

```
..........O.
..........X.
..........O.
..........X.
..........O.
..........X.
..........O.
..........X.
0123456789ab
```

## Timeouts and out-of-memory

Timeouts are the per-side letters of the log (an own timeout also gives an 'Invalid column selected: -1' line). OOM lines (`Memory limit exceeded: N MB used`, then an uncaught SecurityException) are not tagged with a side or bot, at most one per game. Warn = games with memory-warning lines in the log (both bots).

| Bot | Games | Own timeouts | Games with own timeout | Own full-column turns | Games with an OOM line | OOM MB min/median/p90/max | Games with warn lines |
|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | 145 | 118 | 14 | 0 | 101 (69.7%) | 200/237/331/447 | 51 (max 199 MB) |
| Testimine-M7 | 91 | 0 | 0 | 0 | 39 (42.9%) | 200/230/300/331 | 22 (max 199 MB) |
| Testing_v2 | 90 | 334 | 67 | 0 | 61 (67.8%) | 200/237/358/416 | 24 (max 199 MB) |
| Hurt me plenty | 80 | 271 | 50 | 6 | 46 (57.5%) | 200/272.5/372/388 | 18 (max 199 MB) |
| BeetaSaag | 77 | 12 | 2 | 7 | 46 (59.7%) | 200/228.5/376/393 | 25 (max 198 MB) |
| Strange Quark 1 | 77 | 0 | 0 | 0 | 49 (63.6%) | 200/230/284/317 | 24 (max 199 MB) |
| specialmovedestroyer | 64 | 0 | 0 | 0 | 46 (71.9%) | 200/226.5/290/365 | 21 (max 199 MB) |
| Savi5 | 58 | 0 | 0 | 0 | 30 (51.7%) | 200/206.5/264/302 | 24 (max 199 MB) |
| Savi6 - "Beat this" | 57 | 1 | 1 | 0 | 42 (73.7%) | 200/213/240/277 | 25 (max 199 MB) |
| Savi7 - "The Prince" | 52 | 9 | 7 | 0 | 45 (86.5%) | 200/278/416/447 | 14 (max 199 MB) |
| HereWeGo | 48 | 6 | 3 | 0 | 23 (47.9%) | 205/324/366/370 | 8 (max 198 MB) |
| AntiStrat_FINAL | 36 | 0 | 0 | 0 | 11 (30.6%) | 200/227/286/295 | 14 (max 198 MB) |
| \u006c\u0061\u006c\2 | 36 | 0 | 0 | 0 | 19 (52.8%) | 200/216/269/271 | 13 (max 199 MB) |
| lalalabot | 34 | 0 | 0 | 0 | 26 (76.5%) | 200/236/277/280 | 4 (max 199 MB) |
| good night | 33 | 0 | 0 | 0 | 25 (75.8%) | 200/273/318/331 | 2 (max 199 MB) |
| lalalalabot | 33 | 0 | 0 | 0 | 8 (24.2%) | 200/219/274/274 | 15 (max 199 MB) |
| sleep tight | 33 | 0 | 0 | 0 | 33 (100.0%) | 223/265/294/302 | 0 |
| Back To Basics | 31 | 0 | 0 | 0 | 17 (54.8%) | 201/310/355/365 | 6 (max 195 MB) |
| Savi6 - "Beat This" | 25 | 0 | 0 | 0 | 13 (52.0%) | 201/218/230/250 | 14 (max 197 MB) |

340 of 550 games (61.8%) contain an OOM line.

Which side timed out in games with / without an OOM line, seen from each bot (own = only this bot's side, opp = only its opponent's, both, none):

| Bot | OOM games: own / opp / both / none | Other games: own / opp / both / none |
|---|---|---|
| Savi5 beater 2.1 | 7 / 13 / 4 / 77 | 1 / 1 / 2 / 40 |
| Testimine-M7 | 0 / 2 / 0 / 37 | 0 / 4 / 0 / 48 |
| Testing_v2 | 40 / 1 / 7 / 13 | 15 / 1 / 5 / 8 |
| Hurt me plenty | 24 / 0 / 3 / 19 | 18 / 2 / 5 / 9 |
| BeetaSaag | 0 / 4 / 0 / 42 | 0 / 4 / 2 / 25 |
| Strange Quark 1 | 0 / 3 / 0 / 46 | 0 / 1 / 0 / 27 |
| specialmovedestroyer | 0 / 8 / 0 / 38 | 0 / 2 / 0 / 16 |
| Savi5 | 0 / 3 / 0 / 27 | 0 / 7 / 0 / 21 |
| Savi6 - "Beat this" | 1 / 7 / 0 / 34 | 0 / 3 / 0 / 12 |
| Savi7 - "The Prince" | 5 / 11 / 1 / 28 | 1 / 0 / 0 / 6 |
| HereWeGo | 0 / 3 / 3 / 17 | 0 / 0 / 0 / 25 |
| AntiStrat_FINAL | 0 / 2 / 0 / 9 | 0 / 3 / 0 / 22 |
| \u006c\u0061\u006c\2 | 0 / 2 / 0 / 17 | 0 / 1 / 0 / 16 |
| lalalabot | 0 / 4 / 0 / 22 | 0 / 0 / 0 / 8 |
| good night | 0 / 4 / 0 / 21 | 0 / 0 / 0 / 8 |
| lalalalabot | 0 / 2 / 0 / 6 | 0 / 5 / 0 / 20 |
| sleep tight | 0 / 3 / 0 / 30 | 0 / 0 / 0 / 0 |
| Back To Basics | 0 / 3 / 0 / 14 | 0 / 0 / 0 / 14 |
| Savi6 - "Beat This" | 0 / 2 / 0 / 11 | 0 / 1 / 0 / 11 |

First timeout or full-column line at or after the OOM line, by whose side it hit (own / opp / no later event):

| Bot | own / opp / none |
|---|---|
| Savi5 beater 2.1 | 7 / 16 / 78 |
| Testimine-M7 | 0 / 2 / 37 |
| Testing_v2 | 38 / 4 / 19 |
| Hurt me plenty | 24 / 1 / 21 |
| BeetaSaag | 1 / 3 / 42 |
| Strange Quark 1 | 0 / 3 / 46 |
| specialmovedestroyer | 0 / 7 / 39 |
| Savi5 | 0 / 0 / 30 |
| Savi6 - "Beat this" | 1 / 4 / 37 |
| Savi7 - "The Prince" | 5 / 12 / 28 |
| HereWeGo | 2 / 4 / 17 |
| AntiStrat_FINAL | 0 / 2 / 9 |
| \u006c\u0061\u006c\2 | 0 / 1 / 18 |
| lalalabot | 0 / 4 / 22 |
| good night | 0 / 5 / 20 |
| lalalalabot | 0 / 2 / 6 |
| sleep tight | 0 / 3 / 30 |
| Back To Basics | 0 / 3 / 14 |
| Savi6 - "Beat This" | 0 / 2 / 11 |

Games with an OOM line followed by a later timeout line: 78 of 340; delay from the OOM line to it, ms min/median/p90/max = 2/392.5/998/2453.

Games where a timeout line precedes the OOM line: 13.

OOM games / games for every pair of bots (both colours); a bot that exhausts memory shows up as a column and row of high shares:

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Savi5 beater 2.1 | . | 0/7 | 10/10 | 9/13 | 11/14 | 13/14 | 11/14 | 4/5 | 3/3 | 7/7 | 3/7 | 0/7 | 6/6 | 7/7 | 1/7 | 0/6 | 6/6 | 5/5 | 5/7 |
| 2 | Testimine-M7 | 0/7 | . | 0/7 | 2/7 | 0/7 | 3/7 | 6/7 | 2/7 | 3/7 |  |  | 3/7 | 1/7 | 5/7 | 7/7 |  | 7/7 |  |  |
| 3 | Testing_v2 | 10/10 | 0/7 | . | 2/8 | 3/6 | 5/5 | 4/7 | 3/5 | 5/5 | 5/5 | 5/5 | 2/5 | 3/4 | 3/3 | 5/5 | 0/3 | 3/3 | 2/2 | 1/2 |
| 4 | Hurt me plenty | 9/13 | 2/7 | 2/8 | . | 0/4 | 3/4 | 3/3 | 2/7 | 4/7 | 7/7 | 6/6 | 1/2 | 1/2 | 2/2 |  | 0/2 | 2/2 | 2/2 | 0/2 |
| 5 | BeetaSaag | 11/14 | 0/7 | 3/6 | 0/4 | . | 0/2 | 3/3 | 3/7 | 5/7 | 6/6 | 6/6 | 0/2 | 1/2 | 1/2 | 1/1 | 0/2 | 2/2 | 2/2 | 2/2 |
| 6 | Strange Quark 1 | 13/14 | 3/7 | 5/5 | 3/4 | 0/2 | . | 4/4 | 3/6 | 7/7 | 5/7 | 0/5 | 0/2 | 0/2 | 2/2 | 0/2 | 0/2 | 2/2 | 2/2 | 0/2 |
| 7 | specialmovedestroyer | 11/14 | 6/7 | 4/7 | 3/3 | 3/3 | 4/4 | . |  | 5/7 | 6/7 | 0/4 |  |  | 1/2 |  | 0/2 |  | 2/2 | 1/2 |
| 8 | Savi5 | 4/5 | 2/7 | 3/5 | 2/7 | 3/7 | 3/6 |  | . |  |  |  | 3/7 | 3/7 |  |  |  | 7/7 |  |  |
| 9 | Savi6 - "Beat this" | 3/3 | 3/7 | 5/5 | 4/7 | 5/7 | 7/7 | 5/7 |  | . |  |  |  |  | 3/7 | 7/7 |  |  |  |  |
| 10 | Savi7 - "The Prince" | 7/7 |  | 5/5 | 7/7 | 6/6 | 5/7 | 6/7 |  |  | . |  |  |  |  |  | 7/7 |  | 2/6 |  |
| 11 | HereWeGo | 3/7 |  | 5/5 | 6/6 | 6/6 | 0/5 | 0/4 |  |  |  | . |  |  |  |  | 0/5 |  | 0/6 | 3/4 |
| 12 | AntiStrat_FINAL | 0/7 | 3/7 | 2/5 | 1/2 | 0/2 | 0/2 |  | 3/7 |  |  |  | . | 0/2 |  |  |  | 2/2 |  |  |
| 13 | \u006c\u0061\u006c\2 | 6/6 | 1/7 | 3/4 | 1/2 | 1/2 | 0/2 |  | 3/7 |  |  |  | 0/2 | . |  | 2/2 |  | 2/2 |  |  |
| 14 | lalalabot | 7/7 | 5/7 | 3/3 | 2/2 | 1/2 | 2/2 | 1/2 |  | 3/7 |  |  |  |  | . | 2/2 |  |  |  |  |
| 15 | good night | 1/7 | 7/7 | 5/5 |  | 1/1 | 0/2 |  |  | 7/7 |  |  |  | 2/2 | 2/2 | . |  |  |  |  |
| 16 | lalalalabot | 0/6 |  | 0/3 | 0/2 | 0/2 | 0/2 | 0/2 |  |  | 7/7 | 0/5 |  |  |  |  | . |  | 0/2 | 1/2 |
| 17 | sleep tight | 6/6 | 7/7 | 3/3 | 2/2 | 2/2 | 2/2 |  | 7/7 |  |  |  | 2/2 | 2/2 |  |  |  | . |  |  |
| 18 | Back To Basics | 5/5 |  | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 |  |  | 2/6 | 0/6 |  |  |  |  | 0/2 |  | . | 0/2 |
| 19 | Savi6 - "Beat This" | 5/7 |  | 1/2 | 0/2 | 2/2 | 0/2 | 1/2 |  |  |  | 3/4 |  |  |  |  | 1/2 |  | 0/2 | . |

Median OOM size in MB for every pair of bots (the figure is the memory in use when the limit tripped; abrupt jumps give large values, slow growth gives values just above 200):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Savi5 beater 2.1 |  |  | 250 | 297 | 206 | 230 | 278 | 200 | 205 | 426 | 302 |  | 248 | 230 | 306 |  | 274 | 303 | 208 |
| 2 | Testimine-M7 |  |  |  | 207.5 |  | 224 | 225 | 203 | 200 |  |  | 215 | 210 | 229 | 292 |  | 265 |  |  |
| 3 | Testing_v2 | 250 |  |  | 252.5 | 228 | 209 | 216.5 | 202 | 201 | 388 | 341 | 222 | 200 | 227 | 284 |  | 262 | 330 | 201 |
| 4 | Hurt me plenty | 297 | 207.5 | 252.5 |  |  | 238 | 274 | 206 | 208.5 | 377 | 332 | 231 | 201 | 244.5 |  |  | 268 | 303 |  |
| 5 | BeetaSaag | 206 |  | 228 |  |  |  | 252 | 207 | 206 | 385.5 | 332.5 |  | 211 | 249 | 258 |  | 276 | 337.5 | 224.5 |
| 6 | Strange Quark 1 | 230 | 224 | 209 | 238 |  |  | 237 | 203 | 217 | 230 |  |  |  | 270.5 |  |  | 253.5 | 305.5 |  |
| 7 | specialmovedestroyer | 278 | 225 | 216.5 | 274 | 252 | 237 |  |  | 214 | 208 |  |  |  | 203 |  |  |  | 323.5 | 207 |
| 8 | Savi5 | 200 | 203 | 202 | 206 | 207 | 203 |  |  |  |  |  | 214 | 209 |  |  |  | 264 |  |  |
| 9 | Savi6 - "Beat this" | 205 | 200 | 201 | 208.5 | 206 | 217 | 214 |  |  |  |  |  |  | 240 | 213 |  |  |  |  |
| 10 | Savi7 - "The Prince" | 426 |  | 388 | 377 | 385.5 | 230 | 208 |  |  |  |  |  |  |  |  | 220 |  | 212 |  |
| 11 | HereWeGo | 302 |  | 341 | 332 | 332.5 |  |  |  |  |  |  |  |  |  |  |  |  |  | 226 |
| 12 | AntiStrat_FINAL |  | 215 | 222 | 231 |  |  |  | 214 |  |  |  |  |  |  |  |  | 290.5 |  |  |
| 13 | \u006c\u0061\u006c\2 | 248 | 210 | 200 | 201 | 211 |  |  | 209 |  |  |  |  |  |  | 263.5 |  | 264.5 |  |  |
| 14 | lalalabot | 230 | 229 | 227 | 244.5 | 249 | 270.5 | 203 |  | 240 |  |  |  |  |  | 276 |  |  |  |  |
| 15 | good night | 306 | 292 | 284 |  | 258 |  |  |  | 213 |  |  |  | 263.5 | 276 |  |  |  |  |  |
| 16 | lalalalabot |  |  |  |  |  |  |  |  |  | 220 |  |  |  |  |  |  |  |  | 218 |
| 17 | sleep tight | 274 | 265 | 262 | 268 | 276 | 253.5 |  | 264 |  |  |  | 290.5 | 264.5 |  |  |  |  |  |  |
| 18 | Back To Basics | 303 |  | 330 | 303 | 337.5 | 305.5 | 323.5 |  |  | 212 |  |  |  |  |  |  |  |  |  |
| 19 | Savi6 - "Beat This" | 208 |  | 201 |  | 224.5 |  | 207 |  |  |  | 226 |  |  |  |  | 218 |  |  |  |

## Wall-clock evidence

The exported log lines are only ERROR lines (OOM, timeout, invalid column, full column); they carry millisecond timestamps (2194 lines) but there is no per-move or start/end line, so a game's duration can only be bracketed from its first to its last timestamped event.

50 games have two or more paired timeout lines (log events match the skipped turns found by the replay). Between consecutive such events (131 intervals, 771 turns): time per turn in ms, min/median/p90/max = 0/50/64/99, pooled mean 22 ms per turn (17.0 s over 771 turns). First-to-last event span per game, ms min/median/p90/max = 1/261.5/740/1151, covering turns 2/10/34/52. Consecutive timeouts can be logged only a few ms apart although each is reported as exceeding 100 ms, so the log timestamps are not a reliable clock for single moves; they give an order of magnitude only (games of this many turns are logged within about a second, far below 30 s).

