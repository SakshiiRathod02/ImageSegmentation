# High-Performance Computational Image Segmentation & Scalability Benchmarking

An end-to-end, high-performance computational image segmentation suite in Java (JDK 17+) engineered to partition images into meaningful semantic regions, with an empirical investigation into its scalability for **large multi-megapixel images** and **increasing dataset batch sizes**.

---

## 1. Project Overview & Question Addressed

> **Prompt / Objective:**
> *"Q6. Image Segmentation: Develop a computational solution for dividing images into meaningful regions. Investigate its performance for large images and increasing image dataset sizes. You can use any dataset available on portals such as Kaggle related to the broad statement given or create synthetic dataset to test the algorithm developed by you. Use Java and give complete project."*

### Key Features
- **Four Distinct Algorithmic Paradigms**:
  1. **SLIC Superpixels (Achanta et al., TPAMI 2012)**: Simple Linear Iterative Clustering operating in 5D CIELAB + Spatial space with bounded $O(N)$ search and connected-components relabeling.
  2. **K-Means Clustering**: Optimized Lloyd's algorithm with K-Means++ initialization, perceptually uniform CIELAB color space, and parallel pixel assignment.
  3. **Felzenszwalb-Huttenlocher Graph-Based Segmentation (IJCV 2004)**: Minimum Spanning Tree (MST) component merging using Union-Find with path compression and union-by-rank.
  4. **Multi-Level Otsu Thresholding**: Optimal luminance thresholding maximizing between-class histogram variance.
- **Data Generator & Real-World Ingestion**:
  - **Procedural Synthetic Dataset Generator**: Renders multi-object geometric scenes with additive Gaussian sensor noise, gradients, and **exact Ground-Truth label masks**.
  - **Kaggle / Custom Dataset Loader**: Ingests arbitrary folders of PNG, JPEG, JPG, and BMP images.
- **Empirical Scalability Benchmarking Engine**:
  - **Large Image Scaling**: Tests progressive resolutions ($256 \times 256$ up to $3072 \times 3072$, ~9.5 Megapixels).
  - **Increasing Dataset Sizes**: Tests batch throughput ($5, 10, 25, 50$ images) comparing single-threaded sequential vs multi-threaded parallel execution.
  - **Quantitative Quality Metrics**: Calculates **mIoU (Mean Intersection-over-Union)**, **mDice (F1)**, **Pixel Accuracy**, and **Boundary F1-score**.
  - **Visual Output Generation**: Generates 3-panel comparative images `[Original | Mean-Color Segmented | Boundary Contours]`.

---

## 2. Theoretical Formulation & Algorithmic Complexity

| Algorithm | Computational Paradigm | Time Complexity | Space Complexity | Strengths & Trade-offs |
|:---|:---|:---:|:---:|:---|
| **SLIC Superpixels** | Localized k-means clustering in 5D CIELAB space | $\mathcal{O}(N)$ | $\mathcal{O}(N)$ | Strictly linear time with image size; generates compact, edge-conforming regions. Ideal for multi-megapixel images. |
| **K-Means (k=8)** | Global feature space clustering with Lloyd iterations | $\mathcal{O}(N \cdot K \cdot I)$ | $\mathcal{O}(N + K)$ | Excellent color quantization; multithreaded assignment; lacks spatial continuity unless coordinates are appended. |
| **Felzenszwalb Graph** | Minimum Spanning Tree / Kruskal-like greedy edge union | $\mathcal{O}(E \log E)$ where $E \approx 4N$ | $\mathcal{O}(N)$ | Captures non-local semantic boundaries and fine contrast; edge sorting dominates runtime on large images. |
| **Multi-Level Otsu** | Histogram between-class variance maximization | $\mathcal{O}(L^K)$ where $L=256$ | $\mathcal{O}(L)$ | Sub-millisecond execution; operates strictly on intensity distribution. |

---

## 3. Empirical Performance Investigation Results

The following empirical results were measured on a **12-Core host running OpenJDK 17 64-Bit VM**:

### Experiment A: Performance Scaling on Large Images (Multi-Megapixel)

| Algorithm | Resolution | Total Pixels | Runtime (ms) | Throughput (MP/s) |
|:---|:---:|:---:|:---:|:---:|
| **Multi-Level Otsu** | $256 \times 256$ | 65,536 (0.07 MP) | 24 ms | 2.73 MP/s |
| **K-Means** | $256 \times 256$ | 65,536 (0.07 MP) | 77 ms | 0.85 MP/s |
| **SLIC Superpixels** | $256 \times 256$ | 65,536 (0.07 MP) | **29 ms** | **2.26 MP/s** |
| **Felzenszwalb Graph** | $256 \times 256$ | 65,536 (0.07 MP) | 382 ms | 0.17 MP/s |
| *---* | *---* | *---* | *---* | *---* |
| **Multi-Level Otsu** | $1024 \times 1024$ | 1,048,576 (1.05 MP) | 28 ms | 37.45 MP/s |
| **K-Means** | $1024 \times 1024$ | 1,048,576 (1.05 MP) | 485 ms | 2.16 MP/s |
| **SLIC Superpixels** | $1024 \times 1024$ | 1,048,576 (1.05 MP) | **415 ms** | **2.53 MP/s** |
| **Felzenszwalb Graph** | $1024 \times 1024$ | 1,048,576 (1.05 MP) | 5,507 ms | 0.19 MP/s |
| *---* | *---* | *---* | *---* | *---* |
| **Multi-Level Otsu** | $3072 \times 3072$ | 9,437,184 (9.44 MP) | 185 ms | 51.01 MP/s |
| **K-Means** | $3072 \times 3072$ | 9,437,184 (9.44 MP) | 4,466 ms | 2.11 MP/s |
| **SLIC Superpixels** | $3072 \times 3072$ | 9,437,184 (9.44 MP) | **4,336 ms** | **2.18 MP/s** |
| **Felzenszwalb Graph** | $3072 \times 3072$ | 9,437,184 (9.44 MP) | 66,840 ms | 0.14 MP/s |

> **Key Finding for Large Images:**
> - **SLIC Superpixels** scales linearly ($\mathcal{O}(N)$), maintaining a steady throughput of **~2.2 to 2.5 Megapixels per second** regardless of whether the image is 0.07 MP or 9.44 MP.
> - **Felzenszwalb Graph** experiences an $\mathcal{O}(E \log E)$ superlinear runtime penalty at high resolutions due to the necessity of sorting ~37 million edge weights in memory.

---

### Experiment B: Throughput Scaling on Increasing Dataset Sizes (Sequential vs Parallel)

Evaluated using **SLIC Superpixels (k=120, m=15)** across batches of $512 \times 512$ images:

| Batch Size | Sequential (1 Core) | Parallel (12 Cores) | Avg Time / Img (Parallel) | Speedup Factor | Parallel Efficiency |
|:---:|:---:|:---:|:---:|:---:|:---:|
| **5 images** | 593 ms | 141 ms | 28.2 ms | **4.21x** | 35.0% |
| **10 images** | 1,304 ms | 215 ms | 21.5 ms | **6.07x** | 50.5% |
| **25 images** | 3,058 ms | 450 ms | 18.0 ms | **6.80x** | 56.6% |
| **50 images** | 5,527 ms | 863 ms | **17.3 ms** | **6.40x** | 53.4% |

> **Key Finding for Increasing Dataset Sizes:**
> - Batch image segmentation is an **embarrassingly parallel problem**. By utilizing Java's `ForkJoinPool` / parallel streams, throughput accelerates by up to **6.8x**, reducing average processing latency to **17.3 ms per image** (over 57 images/second).

---

### Experiment C: Segmentation Quality vs Ground Truth

Evaluated on synthetic scenes with known ground-truth masks:

| Algorithm | Mean IoU (Jaccard) | Mean Dice (F1) | Pixel Accuracy | Boundary F1 |
|:---|:---:|:---:|:---:|:---:|
| **Felzenszwalb Graph** | **0.9171** | **0.9519** | **98.26%** | **0.9383** |
| **K-Means (k=6, RGB)** | **0.7689** | **0.8293** | **69.82%** | **0.6650** |
| **K-Means (k=6, Spatial-XY)** | 0.5860 | 0.6359 | 50.05% | 0.5248 |
| **SLIC Superpixels (k=80)** | 0.2936 | 0.4270 | 8.37% | 0.3650 |
| **Otsu Multi-Threshold (k=3)** | 0.3343 | 0.4414 | 74.25% | 0.0543 |

---

## 4. Project Directory Structure

```
ImageSegmentationProject/
├── src/main/java/com/segmentation/
│   ├── Main.java                          # CLI and interactive orchestrator
│   ├── algorithm/
│   │   ├── SegmentationAlgorithm.java     # Common contract
│   │   ├── SegmentationResult.java        # Result container & metrics
│   │   ├── KMeansSegmentation.java        # Multithreaded K-Means with K-Means++
│   │   ├── SLICSuperpixelSegmentation.java# O(N) SLIC in 5D CIELAB
│   │   ├── FelzenszwalbSegmentation.java  # Graph MST segmentation
│   │   └── OtsuMultiThreshold.java        # Multi-level Otsu thresholding
│   ├── benchmark/
│   │   ├── BenchmarkRunner.java           # Scalability test engine
│   │   ├── BenchmarkMetric.java           # Record entity
│   │   └── BenchmarkReportExporter.java   # CSV & Markdown report generator
│   ├── dataset/
│   │   ├── SyntheticDatasetGenerator.java # Procedural image + mask generator
│   │   ├── ImageDatasetLoader.java        # Directory scanner for real images
│   │   └── GroundTruthEvaluator.java      # IoU, Dice, Boundary F1 calculation
│   └── util/
│       ├── ColorSpaceConverter.java       # sRGB <-> CIELAB conversions
│       ├── ImageUtils.java                # Overlays, mean-color, composite images
│       └── DisjointSet.java               # Union-Find with rank and path compression
├── bin/                                   # Compiled bytecode
├── output/
│   ├── visuals/                           # Generated 3-panel comparative PNGs
│   ├── BENCHMARK_REPORT.md                # Comprehensive benchmark report
│   ├── benchmark_resolution_scaling.csv   # Resolution benchmark raw data
│   ├── benchmark_dataset_batch_scaling.csv# Batch size benchmark raw data
│   └── benchmark_accuracy_metrics.csv     # Accuracy evaluation raw data
├── build.bat                              # Windows batch compilation script
├── build.ps1                              # PowerShell compilation script
├── run.bat                                # Windows batch execution script
├── run.ps1                                # PowerShell execution script
└── README.md                              # This documentation
```

---

## 5. How to Build and Run

### 5.1 Compilation
Run the build script:
```powershell
.\build.ps1
# or using Command Prompt:
build.bat
```
*(Uses standard `javac -d bin ...` with no external dependencies required!)*

### 5.2 Running the Application

#### Option 1: Interactive Menu (Recommended)
Run with no arguments to access the interactive CLI:
```powershell
.\run.ps1
# or
run.bat
```
Menu options:
1. **Run Full Performance Benchmark & Scaling Investigation** (Runs large images, dataset scaling, accuracy evaluation, and exports CSV/Markdown/Visuals)
2. **Generate Synthetic Dataset with Ground-Truth Masks**
3. **Process Custom / Kaggle Image Dataset Folder**
4. **Segment a Single Image with Visual Comparison Overlays**

#### Option 2: Command-Line Flags
- **Run the full investigation suite**:
  ```powershell
  .\run.ps1 --benchmark output
  ```
- **Generate synthetic dataset** (e.g. 50 images at 512x512):
  ```powershell
  .\run.ps1 --generate-dataset 50 512 512 output/my_dataset
  ```
- **Process custom dataset (e.g., from Kaggle)**:
  ```powershell
  .\run.ps1 --segment-dataset "C:\path\to\kaggle\images" output/kaggle_results
  ```
- **Segment a single test image**:
  ```powershell
  .\run.ps1 --segment-image "C:\path\to\my_image.png" output/single_results
  ```

---

## 6. How to Use with Real-World Kaggle Datasets

You can download any standard image segmentation dataset from Kaggle or external sources:
- **BSDS500 (Berkeley Segmentation Dataset)**
- **Oxford-IIIT Pet Dataset**
- **ISIC Skin Lesion Segmentation Dataset**
- **Cityscapes / CamVid / Pascal VOC**

Once downloaded and extracted into a folder (e.g. `C:\Datasets\KaggleImages`), simply execute:
```powershell
.\run.ps1 --segment-dataset "C:\Datasets\KaggleImages" output/kaggle_segmented
```
The system will automatically detect all `.png`, `.jpg`, `.jpeg`, and `.bmp` files, distribute the workload across all CPU cores in parallel, and generate side-by-side comparative visualizations for every image.
