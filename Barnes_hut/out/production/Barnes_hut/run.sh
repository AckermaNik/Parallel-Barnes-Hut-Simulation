#!/bin/bash

# Config
program="java Main"
input="input1.txt"
steps=2
ser_avg=33.47
threads=4
runs=4

# Array to store times
times=()

echo "THREADS: $threads for steps: $steps and input: $input ..."

for i in $(seq 1 $runs); do
    echo -n "Run $i: "
    
    # Get real time from `time` in seconds using /usr/bin/time
    t=$(/usr/bin/time -f "%e" $program "$input" "$steps" "$threads" 2>&1 >/dev/null)
    echo "$t sec"
    times+=("$t")
done

# Calculate average and variance
sum=0
for t in "${times[@]}"; do
    sum=$(echo "$sum + $t" | bc -l)
done

avg=$(echo "$sum / $runs" | bc -l)

var_sum=0
for t in "${times[@]}"; do
    diff=$(echo "$t - $avg" | bc -l)
    sq=$(echo "$diff * $diff" | bc -l)
    var_sum=$(echo "$var_sum + $sq" | bc -l)
done

variance=$(echo "$var_sum / $runs" | bc -l)

# Speedup: baseline_time / avg
speedup=$(echo "$ser_avg / $avg" | bc -l)

echo
echo "Average Time : $avg seconds"
echo "Variance     : $variance"
echo "Speedup: $speedup"
