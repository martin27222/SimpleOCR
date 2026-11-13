# UVDoc model

`app/src/main/assets/uvdoc.onnx` is the network SimpleOCR uses to flatten photos of curved pages.

| | |
| --- | --- |
| Source | https://github.com/tanguymagne/UVDoc |
| Paper | Verhoeven, Magne, Sorkine-Hornung: "UVDoc: Neural Grid-based Document Unwarping", SIGGRAPH Asia 2023 |
| License | MIT, copy in [LICENSE](LICENSE) (Copyright (c) 2023 Tanguy MAGNE) |
| Source commit | `4c9b82b537057aff2526e6dd118a847cdd072e82` (2024-05-27) |
| Source file | `model/best_model.pkl`, SHA-256 `7e90861b8a516eb4bc51f84bd889cb77275743d2d1d3ca8091951ec9f2b7da23` |
| Converted file | `uvdoc.onnx`, 31,601,637 bytes, SHA-256 `dbdabb61ca9e70e588a36d3c7eca8a86f978693f83b2cdba09ad94a9775c33e7` |
| Converted on | 2026-11-13 with [tools/export_uvdoc_onnx.py](../../tools/export_uvdoc_onnx.py), PyTorch 2.15.0, ONNX opset 17 |

## What was done to it

The weights are unchanged. The conversion only stores the network in the ONNX format and drops
its second output (a 3D grid that is used during training). The export script compares the ONNX
output with the PyTorch output; the largest difference was 5.4e-07.

The file was converted for this project straight from the original checkpoint. It was not taken
from any third-party conversion.

## Reproduce

```bash
git clone https://github.com/tanguymagne/UVDoc
git -C UVDoc checkout 4c9b82b537057aff2526e6dd118a847cdd072e82
pip install torch onnx onnxruntime numpy
python tools/export_uvdoc_onnx.py --uvdoc-repo UVDoc --out app/src/main/assets/uvdoc.onnx
```

The SHA-256 of the result can differ between PyTorch versions; the weights inside do not.

## License scope

The UVDoc repository is published under the MIT License and contains the checkpoint itself. It
states no separate terms for the weights, so the MIT License is taken to cover them.
