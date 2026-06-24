// Program.cs
using System;

Console.WriteLine("Welcome to the .NET Sample Application!");
Console.Write("Please enter your name: ");

string? userName = Console.ReadLine();

if (!string.IsNullOrWhiteSpace(userName))
{
    string upperName = userName.ToUpper();
    Console.WriteLine($"Hello, {upperName}! Welcome aboard.");
}
else
{
    Console.WriteLine("Hello, anonymous user!");
}
