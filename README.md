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
  - **Increasing Dataset Sizes**: Tests batch throughput ($5, 10, 25, 50$ images) comparing sequential execution with the configured worker pool.
  - **Configurable Thread Scaling**: Compares distinct valid pool sizes up to the machine's available logical processor count using repeated, median-timed runs.
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

The following benchmark tables are the project's previously recorded example run on a **12-logical-processor host running OpenJDK 17**. They are historical results, not measurements from the current computer. Run the benchmark to generate current CSV and Markdown results; the dashboard now reads the generated CSV data.

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
│   │   ├── BenchmarkMetric.java           # Batch and resolution measurements
│   │   ├── ThreadScalingMetric.java       # Thread scaling measurement
│   │   ├── BenchmarkChartViewer.java      # Swing charts from measured CSV data
│   │   └── BenchmarkReportExporter.java   # CSV & Markdown report generator
│   ├── dataset/
│   │   ├── SyntheticDatasetGenerator.java # Procedural image + mask generator
│   │   ├── ImageDatasetLoader.java        # Directory scanner for real images
│   │   └── GroundTruthEvaluator.java      # IoU, Dice, Boundary F1 calculation
│   └── util/
│       ├── ColorSpaceConverter.java       # sRGB <-> CIELAB conversions
│       ├── ImageUtils.java                # Overlays, mean-color, composite images
│       ├── DisjointSet.java               # Union-Find with rank and path compression
│       └── ThreadConfig.java              # Configurable managed ForkJoinPool
├── bin/                                   # Compiled bytecode
├── output/
│   ├── visuals/                           # Generated 3-panel comparative PNGs
│   ├── BENCHMARK_REPORT.md                # Comprehensive benchmark report
│   ├── benchmark_resolution_scaling.csv   # Resolution benchmark raw data
│   ├── benchmark_dataset_batch_scaling.csv# Batch size benchmark raw data
│   ├── benchmark_thread_scaling.csv       # Optional thread-count scaling measurements
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

Run the dependency-free regression tests with:
```text
test.bat
```

### 5.2 Running the Application

#### Option 1: Interactive Menu (Recommended)
Run with no arguments to access the interactive CLI:
```powershell
.\run.ps1
# or
run.bat
```
Menu options:
1. **Run benchmark using the configured worker count**
2. **Run the full benchmark with a thread-scaling experiment**
3. **Display the measured-data dashboard**
4. **Generate a synthetic dataset with ground-truth masks**
5. **Process a custom image dataset folder**
6. **Segment a single image**
7. **Configure the number of worker threads**
8. **Exit**

#### Option 2: Command-Line Flags
- **Run the full investigation suite**:
  ```powershell
  .\run.ps1 --benchmark output
  ```
- **Run the suite with the additional thread-scaling experiment**:
  ```powershell
  .\run.ps1 --thread-scaling output
  ```
- **Choose a worker count for a command** (`--threads` goes before the command):
  ```powershell
  .\run.ps1 --threads 4 --benchmark output
  .\run.ps1 --threads 2 --segment-dataset "C:\path\to\images" output/results
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
The system will automatically detect all `.png`, `.jpg`, `.jpeg`, and `.bmp` files, distribute the workload across the configured worker pool, and generate side-by-side comparative visualizations for every image.

## 7. Thread configuration and benchmark interpretation

The interactive menu's **Configure the number of worker threads** option changes the pool used by later operations without restarting the application. A command-line run can set the count with `--threads <positive-count>` before the command. The default is `Runtime.getRuntime().availableProcessors()`. The application displays the available logical-processor count; this value is not a guarantee that the same number of workers will be busy at every moment.

`ThreadConfig.java` owns the reusable application-wide `ForkJoinPool`. Batch image processing submits its parallel stream to this pool rather than using the common pool. Choosing one worker uses a sequential path. K-Means pixel assignment uses the configured pool when more than one worker is selected; SLIC, Otsu, and Felzenszwalb remain sequential for a single image. The ordinary batch benchmark uses the selected count. The optional scaling experiment tests distinct valid counts from 1, 2, 4, 6, 8, and the available logical processor count, excluding any count above the machine's reported availability.

The thread-scaling experiment uses one fixed generated batch and identical SLIC settings at every pool size. It performs a warm-up and reports the median of three measured runs. Dataset generation and file output are outside the timed intervals. `benchmark_thread_scaling.csv` records configured threads, actual pool parallelism, runtimes, speedup, efficiency, throughput, accuracy applicability, and failures. Accuracy is not measured in this performance-only experiment.

Speedup is sequential runtime divided by parallel runtime. Parallel efficiency is speedup divided by configured thread count, expressed as a percentage. More threads do not guarantee proportional improvement: task scheduling, memory bandwidth, workload size, and non-parallel portions can limit scaling. The Swing charts read measured values from generated CSV files and display a clear message when the required measurements are absent or invalid.
