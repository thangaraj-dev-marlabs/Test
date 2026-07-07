Console.WriteLine("=== Addition Program ===");

// Method 1: Add two predefined numbers
int number1 = 10;
int number2 = 20;
int sum = number1 + number2;

Console.WriteLine($"Adding {number1} + {number2} = {sum}");

// Method 2: Add two numbers with user input
Console.WriteLine("\nEnter first number:");
if (int.TryParse(Console.ReadLine(), out int userNumber1))
{
    Console.WriteLine("Enter second number:");
    if (int.TryParse(Console.ReadLine(), out int userNumber2))
    {
        int userSum = userNumber1 + userNumber2;
        Console.WriteLine($"Result: {userNumber1} + {userNumber2} = {userSum}");
    }
    else
    {
        Console.WriteLine("Invalid second number!");
    }
}
else
{
    Console.WriteLine("Invalid first number!");
}
