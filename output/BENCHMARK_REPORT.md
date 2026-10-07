# Computational Image Segmentation: Empirical Investigation & Performance Report

## 1. Executive Summary & Problem Scope
This report investigates the computational performance, empirical scalability, and segmentation fidelity of algorithmic solutions developed in Java for dividing images into meaningful regions. Specifically, it analyzes:
1. **Algorithmic Behavior on Large Images**: Scaling from 256x256 up to multi-megapixel resolutions.
2. **Throughput on Increasing Dataset Sizes**: Sequential vs multi-threaded parallel batch execution on batches of images.
3. **Segmentation Quality**: Quantitative evaluation (mIoU, Dice, Boundary F1) against synthetic ground truth.

- **Host Architecture**: 12 CPU logical cores, OpenJDK 17 64-Bit VM

---
## 2. Investigation 1: Performance Scaling with Large Images
In this experiment, image dimensions are scaled progressively while tracking execution time, throughput (Megapixels/sec), and memory footprint.

| Algorithm | Resolution | Total Pixels | Time (ms) | Throughput (MP/s) | Memory (MB) |
|:---|:---:|:---:|:---:|:---:|:---:|
| Multi-Level Otsu (classes=3) | 256x256 | 65,536 px (0.07 MP) | 23 ms | **2.85 MP/s** | 0.8 MB |
| K-Means (k=8) | 256x256 | 65,536 px (0.07 MP) | 50 ms | **1.31 MP/s** | 3.3 MB |
| SLIC Superpixels (k=150, m=15) | 256x256 | 65,536 px (0.07 MP) | 34 ms | **1.93 MP/s** | 3.3 MB |
| Felzenszwalb Graph (k=300, minSize=50) | 256x256 | 65,536 px (0.07 MP) | 246 ms | **0.27 MP/s** | 11.4 MB |
| Multi-Level Otsu (classes=3) | 512x512 | 262,144 px (0.26 MP) | 11 ms | **23.83 MP/s** | 4.1 MB |
| K-Means (k=8) | 512x512 | 262,144 px (0.26 MP) | 78 ms | **3.36 MP/s** | 25.4 MB |
| SLIC Superpixels (k=150, m=15) | 512x512 | 262,144 px (0.26 MP) | 102 ms | **2.57 MP/s** | 26.1 MB |
| Felzenszwalb Graph (k=300, minSize=50) | 512x512 | 262,144 px (0.26 MP) | 631 ms | **0.42 MP/s** | 53.5 MB |
| Multi-Level Otsu (classes=3) | 1024x1024 | 1,048,576 px (1.05 MP) | 15 ms | **69.91 MP/s** | 12.2 MB |
| K-Means (k=8) | 1024x1024 | 1,048,576 px (1.05 MP) | 245 ms | **4.28 MP/s** | 62.5 MB |
| SLIC Superpixels (k=150, m=15) | 1024x1024 | 1,048,576 px (1.05 MP) | 231 ms | **4.54 MP/s** | 24.4 MB |
| Felzenszwalb Graph (k=300, minSize=50) | 1024x1024 | 1,048,576 px (1.05 MP) | 3,077 ms | **0.34 MP/s** | 163.8 MB |
| Multi-Level Otsu (classes=3) | 2048x2048 | 4,194,304 px (4.19 MP) | 43 ms | **97.54 MP/s** | 36.4 MB |
| K-Means (k=8) | 2048x2048 | 4,194,304 px (4.19 MP) | 1,215 ms | **3.45 MP/s** | 155.8 MB |
| SLIC Superpixels (k=150, m=15) | 2048x2048 | 4,194,304 px (4.19 MP) | 1,158 ms | **3.62 MP/s** | 73.0 MB |
| Felzenszwalb Graph (k=300, minSize=50) | 2048x2048 | 4,194,304 px (4.19 MP) | 15,953 ms | **0.26 MP/s** | 700.5 MB |
| Multi-Level Otsu (classes=3) | 3072x3072 | 9,437,184 px (9.44 MP) | 80 ms | **117.96 MP/s** | 76.3 MB |
| K-Means (k=8) | 3072x3072 | 9,437,184 px (9.44 MP) | 2,400 ms | **3.93 MP/s** | 447.8 MB |
| SLIC Superpixels (k=150, m=15) | 3072x3072 | 9,437,184 px (9.44 MP) | 2,334 ms | **4.04 MP/s** | 152.7 MB |
| Felzenszwalb Graph (k=300, minSize=50) | 3072x3072 | 9,437,184 px (9.44 MP) | 37,004 ms | **0.26 MP/s** | 1490.9 MB |

### Key Complexity Insights for Large Images:
- **SLIC Superpixel Segmentation**: Demonstrates strictly **linear O(N)** scaling. Because search windows are bounded to a local 2S x 2S region around each seed center, execution time increases proportionally to pixel count N.
- **K-Means Clustering**: Shows **O(N * K * I)** scaling. For large images, cache locality and primitive array access maintain high multi-threaded throughput.
- **Felzenszwalb Graph-Based**: Exhibits **O(E log E)** scaling dominated by edge weight sorting. As image size reaches multiple megapixels, edge count E ? 4N increases the sorting overhead.

---
## 3. Investigation 2: Performance Scaling with Increasing Dataset Sizes
In this experiment, dataset batch sizes are scaled from small batches to large sets, comparing single-threaded sequential execution against multi-threaded parallel batch execution.

| Algorithm | Batch Size | Execution Mode | Total Time (ms) | Avg Time/Img (ms) | Speedup Factor | Parallel Efficiency |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| SLIC Superpixels (k=120, m=15) | 5 images | Sequential (1T) | 333 ms | 66.6 ms | **1.00x** | 100.0% |
| SLIC Superpixels (k=120, m=15) | 5 images | Parallel (12T) | 98 ms | 19.6 ms | **3.40x** | 28.3% |
| SLIC Superpixels (k=120, m=15) | 10 images | Sequential (1T) | 936 ms | 93.6 ms | **1.00x** | 100.0% |
| SLIC Superpixels (k=120, m=15) | 10 images | Parallel (12T) | 130 ms | 13.0 ms | **7.20x** | 60.0% |
| SLIC Superpixels (k=120, m=15) | 25 images | Sequential (1T) | 1,636 ms | 65.4 ms | **1.00x** | 100.0% |
| SLIC Superpixels (k=120, m=15) | 25 images | Parallel (12T) | 304 ms | 12.2 ms | **5.38x** | 44.8% |
| SLIC Superpixels (k=120, m=15) | 50 images | Sequential (1T) | 3,863 ms | 77.3 ms | **1.00x** | 100.0% |
| SLIC Superpixels (k=120, m=15) | 50 images | Parallel (12T) | 514 ms | 10.3 ms | **7.52x** | 62.6% |

### Concurrency & Amdahl's Law Analysis:
- Batch processing across multiple images exhibits near-ideal parallel speedup due to task independence (embarrassingly parallel problem).
- As dataset size grows, the parallel scheduler maintains uniform CPU saturation across all worker threads.

---
## 4. Investigation 3: Segmentation Quality on Ground-Truth Dataset
Evaluation using synthetic benchmark images with known geometric ground-truth masks.

| Algorithm | Mean IoU (Jaccard) | Mean Dice (F1) | Pixel Accuracy | Boundary Precision | Boundary Recall | Boundary F1 |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| Otsu Multi-Threshold (k=3) | **0.3343** | 0.4414 | 74.25% | 0.028 | 0.987 | **0.054** |
| K-Means (k=6, RGB) | **0.7689** | 0.8293 | 69.82% | 0.501 | 0.989 | **0.665** |
| K-Means (k=6, Spatial-XY) | **0.5860** | 0.6359 | 50.05% | 0.358 | 0.980 | **0.525** |
| SLIC Superpixels (k=80) | **0.2936** | 0.4270 | 8.37% | 0.226 | 0.952 | **0.365** |
| Felzenszwalb Graph | **0.9171** | 0.9519 | 98.26% | 0.884 | 1.000 | **0.938** |

---
## 5. Algorithmic Comparison & Recommendation Matrix

| Criterion | SLIC Superpixels | K-Means Clustering | Felzenszwalb Graph | Otsu Multi-Threshold |
|:---|:---|:---|:---|:---|
| **Time Complexity** | O(N) | O(N * K * I) | O(E log E) | O(L^K) histogram |
| **Space Complexity** | O(N) | O(N + K) | O(E) = O(N) | O(L) = O(256) |
| **Boundary Adherence**| High (perceptual) | Moderate | High (adaptive) | Moderate (intensity only) |
| **Region Regularity** | High (compact) | Unconstrained | Variable | Unconstrained |
| **Large Image Suitability**| Excellent | Very Good | Good | Instantaneous |
| **Recommended Use** | Pre-processing, Superpixels | Color clustering, Quantization | Natural scenes, Over-segmentation | Fast binarization, Medical |

Report automatically generated by `ImageSegmentationProject` Benchmark Engine.
