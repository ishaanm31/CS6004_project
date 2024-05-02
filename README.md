# Steps to Run Analysis

## Null Transformer Analysis

### Step 1: Compile Null-Transformer Java code

```bash
javac -cp .:soot2.jar PA4_null.java
```

### Step 2: Run the Null-Transformer analysis

```bash
java -cp .:soot2.jar PA4_null 
```
### Step 3: To get GC stats for 100 runs for modified program (Run in sootOutput directory)

```bash
chmod +x ./run_java_heap_resize.sh
./run_java_heap_resize.sh
```
### Step 4: To get GC stats for 100 runs for original program (Run in testcase directory)

```bash
cd testcase
chmod +x ./run_java_heap_resize.sh
./run_java_heap_resize.sh
```
### Step 5: To compare GC stats original and modified program

```bash
python take_average.py
```
