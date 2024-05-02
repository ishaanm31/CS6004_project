# Open the text file
with open('summary_og.txt', 'r') as file:
    lines = file.readlines()[1:]  # Skip the header line

# Initialize variables to store count and total sum
count = 0
total_sum = 0

# Loop through each line and calculate sum
for line in lines:
    # Split the line by comma and get the second column value
    values = line.strip().split(', ')
    amount = int(values[1])

    # Add the amount to the total sum
    total_sum += amount

    # Increment the count
    count += 1

# Calculate the average
average = total_sum / count

# Print the average
print("Average heap-resize for original program:", average)


# Open the text file
with open('summary.txt', 'r') as file:
    lines = file.readlines()[1:]  # Skip the header line

# Initialize variables to store count and total sum
count = 0
total_sum = 0

# Loop through each line and calculate sum
for line in lines:
    # Split the line by comma and get the second column value
    values = line.strip().split(', ')
    amount = int(values[1])

    # Add the amount to the total sum
    total_sum += amount

    # Increment the count
    count += 1

# Calculate the average
average = total_sum / count

# Print the average
print("Average heap-resize for modified program:", average)
