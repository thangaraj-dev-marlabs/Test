# Ask the user for their name
puts "What is your name?"
name = gets.chomp

# Greet the user using string interpolation
puts "Hello, #{name}! Let's calculate the area of a rectangle."

# Get the length of the rectangle
puts "Enter the length:"
length = gets.chomp.to_f

# Get the width of the rectangle
pus "Enter the width:"
width = gets.chomp.to_f

# Calculate the area
area = length * width;

# Display the final result
puts "Thanks #{name}! The total area is #{area} square units."
