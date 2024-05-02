#!/bin/bash

# Output file to store results
output_file="../summary.txt"
echo "Count of <heap-resize> tags, Net Amount" > "$output_file"

# Loop to process each file
for i in $(seq 1 100); do
    # Assuming your Java command generates gc_$i.log each time it runs
    java -Xverbosegclog:gc_$i.log -Xint test_null_bench

    # Count the number of <heap-resize> tags
    count=$(grep -c '<heap-resize' gc_$i.log)

    # Calculate the net sum of the 'amount' attributes based on the 'type'
    sum=$(grep '<heap-resize' gc_$i.log | awk -F'type="' '{
        getline amount; 
        amount_val = substr(amount, index(amount, "amount=\"") + 8);
        amount_val = substr(amount_val, 1, index(amount_val, "\"") - 1);

        if ($2 ~ /expand/) {
            total += amount_val;
        } else if ($2 ~ /contract/) {
            total -= amount_val;
        }
    } END {print total}')

    # Write results to output file
    echo "$count, $sum" >> "$output_file"
done

echo "Processing complete."
