# Model files

The three ensemble model files belong in this folder:

| File | Size |
|---|---|
| `model_b3_s42.onnx` | 44,498,579 bytes |
| `model_b3_s7.onnx` | 44,498,579 bytes |
| `model_b3_s123.onnx` | 44,498,579 bytes |

They are **not stored in this repository** — each file is ~44.5 MB, above
GitHub's 25 MB per-file web upload limit.

## How to get them

The models are already inside the released app. Download
**Breast_Ultrasound_AI.apk** from this repository's
[Releases](../../../../releases) page, rename it to `.zip`, and extract:

```
assets/models/model_b3_s42.onnx
assets/models/model_b3_s7.onnx
assets/models/model_b3_s123.onnx
```

Copy the three files into this folder (`app/src/main/assets/models/`),
next to `cam_weights_ensemble.json`, then build the app.

The same three files also power the live web version — they are the
EfficientNet-B3 ensemble members (seeds 42, 7 and 123) from the
Breast Ultrasound Classification project.
