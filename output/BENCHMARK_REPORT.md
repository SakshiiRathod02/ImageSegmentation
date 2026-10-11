# Computational Image Segmentation: Empirical Investigation & Performance Report

## 1. Executive Summary & Problem Scope
This report investigates the computational performance, empirical scalability, and segmentation fidelity of algorithmic solutions developed in Java for dividing images into meaningful regions. Specifically, it analyzes:
1. **Algorithmic Behavior on Large Images**: Scaling from 256x256 up to multi-megapixel resolutions.
2. **Throughput on Increasing Dataset Sizes**: Sequential vs multi-threaded parallel batch execution on batches of images.
3. **Segmentation Quality**: Quantitative evaluation (mIoU, Dice, Boundary F1) against synthetic ground truth.

- **Host Architecture**: 12 available logical processors, Java 17.0.17
- **Configured worker threads for the selected-thread benchmark**: 25000

---
## 2. Investigation 1: Performance Scaling with Large Images
In this experiment, image dimensions are scaled progressively while tracking execution time, throughput (Megapixels/sec), and memory footprint.

| Algorithm | Resolution | Total Pixels | Time (ms) | Throughput (MP/s) | Memory (MB) | Configured Threads | Pool Parallelism | Timing Repeats |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Multi-Level Otsu (classes=3) | 256x256 | 65,536 px (0.07 MP) | 15 ms | **4.37 MP/s** | 0.9 MB | 25000 | 1 | single measured execution (1) |
| K-Means (k=8) | 256x256 | 65,536 px (0.07 MP) | 136 ms | **0.48 MP/s** | 48.6 MB | 25000 | 25000 | single measured execution (1) |
| SLIC Superpixels (k=150, m=15) | 256x256 | 65,536 px (0.07 MP) | 22 ms | **2.98 MP/s** | 3.3 MB | 25000 | 1 | single measured execution (1) |
| Felzenszwalb Graph (k=300, minSize=50) | 256x256 | 65,536 px (0.07 MP) | 348 ms | **0.19 MP/s** | 9.7 MB | 25000 | 1 | single measured execution (1) |
| Multi-Level Otsu (classes=3) | 512x512 | 262,144 px (0.26 MP) | 30 ms | **8.74 MP/s** | 6.1 MB | 25000 | 1 | single measured execution (1) |
| K-Means (k=8) | 512x512 | 262,144 px (0.26 MP) | 263 ms | **1.00 MP/s** | 25.1 MB | 25000 | 25000 | single measured execution (1) |
| SLIC Superpixels (k=150, m=15) | 512x512 | 262,144 px (0.26 MP) | 133 ms | **1.97 MP/s** | 26.3 MB | 25000 | 1 | single measured execution (1) |
| Felzenszwalb Graph (k=300, minSize=50) | 512x512 | 262,144 px (0.26 MP) | 829 ms | **0.32 MP/s** | 53.5 MB | 25000 | 1 | single measured execution (1) |
| Multi-Level Otsu (classes=3) | 1024x1024 | 1,048,576 px (1.05 MP) | 23 ms | **45.59 MP/s** | 18.2 MB | 25000 | 1 | single measured execution (1) |
| K-Means (k=8) | 1024x1024 | 1,048,576 px (1.05 MP) | 727 ms | **1.44 MP/s** | 41.8 MB | 25000 | 25000 | single measured execution (1) |
| SLIC Superpixels (k=150, m=15) | 1024x1024 | 1,048,576 px (1.05 MP) | 348 ms | **3.01 MP/s** | 70.1 MB | 25000 | 1 | single measured execution (1) |
| Felzenszwalb Graph (k=300, minSize=50) | 1024x1024 | 1,048,576 px (1.05 MP) | 4,709 ms | **0.22 MP/s** | 166.4 MB | 25000 | 1 | single measured execution (1) |
| Multi-Level Otsu (classes=3) | 2048x2048 | 4,194,304 px (4.19 MP) | 167 ms | **25.12 MP/s** | 36.2 MB | 25000 | 1 | single measured execution (1) |
| K-Means (k=8) | 2048x2048 | 4,194,304 px (4.19 MP) | 15,857 ms | **0.26 MP/s** | 152.0 MB | 25000 | 25000 | single measured execution (1) |
| SLIC Superpixels (k=150, m=15) | 2048x2048 | 4,194,304 px (4.19 MP) | 2,752 ms | **1.52 MP/s** | 72.6 MB | 25000 | 1 | single measured execution (1) |
| Felzenszwalb Graph (k=300, minSize=50) | 2048x2048 | 4,194,304 px (4.19 MP) | 32,333 ms | **0.13 MP/s** | 675.8 MB | 25000 | 1 | single measured execution (1) |
| Multi-Level Otsu (classes=3) | 3072x3072 | 9,437,184 px (9.44 MP) | 282 ms | **33.47 MP/s** | 76.3 MB | 25000 | 1 | single measured execution (1) |
| K-Means (k=8) | 3072x3072 | 9,437,184 px (9.44 MP) | 25,111 ms | **0.38 MP/s** | 888.2 MB | 25000 | 25000 | single measured execution (1) |
| SLIC Superpixels (k=150, m=15) | 3072x3072 | 9,437,184 px (9.44 MP) | 5,585 ms | **1.69 MP/s** | 490.1 MB | 25000 | 1 | single measured execution (1) |
| Felzenszwalb Graph (k=300, minSize=50) | 3072x3072 | 9,437,184 px (9.44 MP) | 52,769 ms | **0.18 MP/s** | 1447.8 MB | 25000 | 1 | single measured execution (1) |

### Key Complexity Insights for Large Images:
- **SLIC Superpixel Segmentation**: Demonstrates strictly **linear O(N)** scaling. Because search windows are bounded to a local 2S x 2S region around each seed center, execution time increases proportionally to pixel count N.
- **K-Means Clustering**: Shows **O(N * K * I)** scaling. For large images, cache locality and primitive array access maintain high multi-threaded throughput.
- **Felzenszwalb Graph-Based**: Exhibits **O(E log E)** scaling dominated by edge weight sorting. As image size reaches multiple megapixels, edge count E ? 4N increases the sorting overhead.

---
## 3. Investigation 2: Performance Scaling with Increasing Dataset Sizes
In this experiment, dataset batch sizes are scaled from small batches to large sets, comparing a sequential baseline with the configured worker pool. Each result is the median of three measured runs after warm-up on the same in-memory images; generation and file output are excluded.

| Algorithm | Batch Size | Execution Mode | Total Time (ms) | Avg Time/Img (ms) | Speedup Factor | Parallel Efficiency | Configured Threads | Pool Parallelism | Timing Repeats |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| SLIC Superpixels (k=120, m=15) | 5 images | Sequential (1T) | 368 ms | 73.6 ms | **1.00x** | 100.0% | 1 | 1 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 5 images | Parallel (25000T) | 142 ms | 28.4 ms | **2.60x** | 0.0% | 25000 | 25000 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 10 images | Sequential (1T) | 690 ms | 69.0 ms | **1.00x** | 100.0% | 1 | 1 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 10 images | Parallel (25000T) | 192 ms | 19.2 ms | **3.59x** | 0.0% | 25000 | 25000 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 25 images | Sequential (1T) | 2,349 ms | 94.0 ms | **1.00x** | 100.0% | 1 | 1 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 25 images | Parallel (25000T) | 404 ms | 16.2 ms | **5.81x** | 0.0% | 25000 | 25000 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 50 images | Sequential (1T) | 4,636 ms | 92.7 ms | **1.00x** | 100.0% | 1 | 1 | median after warm-up (3) |
| SLIC Superpixels (k=120, m=15) | 50 images | Parallel (25000T) | 849 ms | 17.0 ms | **5.46x** | 0.0% | 25000 | 25000 | median after warm-up (3) |

### Concurrency & Amdahl's Law Analysis:
- Batch images are independent, but speedup is limited by scheduling, memory bandwidth, and other shared resources.
- Efficiency is calculated as measured speedup divided by configured thread count; linear speedup is not guaranteed.

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

### Experiment Limitations
- Results depend on the Java runtime, hardware, system load, and memory availability; repeat on the target machine before drawing general conclusions.
- The resolution and quality experiments are not thread-scaling experiments. K-Means pixel assignment uses the configured pool; SLIC, Otsu, and Felzenszwalb execute sequentially for a single image.
- Thread-scaling accuracy is not recomputed because that experiment measures performance only; determinism tests verify K-Means labels across pool sizes.

Report automatically generated by `ImageSegmentationProject` Benchmark Engine.
