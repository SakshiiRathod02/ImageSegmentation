@echo off
REM Run Image Segmentation Application
REM Allocating up to 4GB of heap for multi-megapixel image processing
java -Xmx4g -cp bin com.segmentation.Main %*
