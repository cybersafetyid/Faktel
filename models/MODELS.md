# Models

Faktel is a pipeline library. The neural-network weights are separate artifacts with **their own licences**.
Two small, permissively licensed models are committed here so the library works out of the box.

| File | Purpose | Size | Licence | SHA-256 |
|---|---|---|---|---|
| `face_detection_yunet_2023mar.onnx` | Face detection + 5 landmarks | 227 KB | MIT | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| `minifasnet_v2.onnx` | Passive liveness | 1.7 MB | Apache-2.0 | `b32929adc2d9c34b9486f8c4c7bc97c1b69bc0ea9befefc380e4faae4e463907` |

Verify with `shasum -a 256 models/*.onnx`.

## Contracts (what Faktel expects from each model)

| Model | Input | Output |
|---|---|---|
| YuNet | `input` float32 `[1,3,640,640]`, **BGR, 0..255** | `cls_/obj_/bbox_/kps_` x strides 8/16/32 |
| MiniFASNetV2 | `input` float32 `[N,3,80,80]`, **BGR, 0..255**, crop = face box x2.7 | 3 logits, **class 1 = real** |
| ArcFace-style embedder | float32 `[1,3,112,112]`, **RGB, (v-127.5)/127.5**, aligned with the 5-point template | one vector (typically 512-d) |

Sources: YuNet from the [OpenCV Zoo](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet);
MiniFASNetV2 ONNX export from [yakhyo/face-anti-spoofing](https://github.com/yakhyo/face-anti-spoofing) (original
[Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing)).

## The embedding model is bring-your-own - on purpose

Selfie-to-KTP matching needs a face-embedding network. The commonly downloaded ones (InsightFace `buffalo` /
`w600k_mbf.onnx`) are **restricted to non-commercial research use**, so Faktel does not redistribute them.
Options:

1. Use a model whose licence you have checked for your use case (permissive MobileFaceNet/ArcFace exports exist;
   licences vary per weights file, not per code repository).
2. Train or fine-tune your own.
3. Match server-side and skip on-device embedding.

`ArcFaceEmbedder` works with any ONNX model that follows the contract above. Faktel's tests exercised it with a
512-d MobileFaceNet export; that file is not shipped.

## Adding another model

1. Check the **weights'** licence (not just the code's).
2. Document contract, source, size, licence and SHA-256 in this file.
3. Add a class implementing the relevant interface (`FaceDetector`, `LivenessDetector`, `FaceEmbedder`,
   `KtpDetector`) - see [CONTRIBUTING](../CONTRIBUTING.md).
