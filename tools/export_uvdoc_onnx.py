#!/usr/bin/env python3
"""Export the UVDoc dewarping network to ONNX.

Input : the original checkpoint model/best_model.pkl from
        https://github.com/tanguymagne/UVDoc (MIT License).
Output: an ONNX file with one input and one output.
          image: float32 [1, 3, 712, 488], RGB, values 0..1
          grid : float32 [1, 2, 45, 31], channel 0 = x, channel 1 = y,
                 each in -1..1. For every node of a regular grid over the
                 flattened page it says where to read in the photo.

Usage:
    git clone https://github.com/tanguymagne/UVDoc
    pip install torch onnx onnxruntime numpy
    python tools/export_uvdoc_onnx.py --uvdoc-repo UVDoc --out uvdoc.onnx
"""
import argparse
import hashlib
import pathlib
import sys

import numpy as np
import torch

IMG_W, IMG_H = 488, 712


class Grid2D(torch.nn.Module):
    """Keeps only the 2D grid; the 3D output is a training aid."""

    def __init__(self, net):
        super().__init__()
        self.net = net

    def forward(self, image):
        return self.net(image)[0]


def sha256(path):
    return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--uvdoc-repo", required=True, help="checkout of tanguymagne/UVDoc")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    repo = pathlib.Path(args.uvdoc_repo)
    sys.path.insert(0, str(repo))
    from model import UVDocnet  # noqa: E402  (defined in the UVDoc checkout)

    checkpoint = repo / "model" / "best_model.pkl"
    # weights_only=True refuses anything but plain tensors, so no code runs on load.
    state = torch.load(checkpoint, map_location="cpu", weights_only=True)
    net = UVDocnet(num_filter=32, kernel_size=5)
    net.load_state_dict(state["model_state"])
    model = Grid2D(net).eval()

    dummy = torch.rand(1, 3, IMG_H, IMG_W)
    torch.onnx.export(
        model,
        dummy,
        args.out,
        input_names=["image"],
        output_names=["grid"],
        opset_version=17,
        dynamo=False,
    )

    import onnxruntime as ort

    session = ort.InferenceSession(args.out, providers=["CPUExecutionProvider"])
    with torch.no_grad():
        expected = model(dummy).numpy()
    actual = session.run(None, {"image": dummy.numpy()})[0]
    max_diff = float(np.abs(expected - actual).max())
    print(f"checkpoint sha256: {sha256(checkpoint)}")
    print(f"onnx sha256      : {sha256(args.out)}")
    print(f"onnx bytes       : {pathlib.Path(args.out).stat().st_size}")
    print(f"max |torch - onnx|: {max_diff:.2e}")
    if max_diff > 1e-3:
        raise SystemExit("ONNX output differs from the PyTorch model")


if __name__ == "__main__":
    main()
