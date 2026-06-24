package main

import (
	"fmt"
	"time"
)

// Item defines a struct with basic fields
type Item struct {
	Name  string
	Price float64
}

// Display prints the details of the item (Method)
func (i Item) Display() {
	fmt.Printf("Item: %s | Price: $%.2f\n", i.Name, i.Price)
}

// processTask simulates an asynchronous task using channels
func processTask(id int, ch chan string) {
	time.Sleep(500 * time.Millisecond) // Simulate work
	ch <- fmt.Sprintf("Task %d completed", id)
}

func main() {
	// 1. Basic Print
	fmt.Println("--- Welcome to Go! ---")

	// 2. Variables and Structs
	book := Item{Name: "The Go Programming Language", Price: 34.99}
	book.Display()

	// 3. Control Structures (If/Else and Slices)
	scores := []int{85, 92, 78, 60}
	fmt.Print("Passing scores: ")
	for _, score := range scores {
		if score >= 70 {
			fmt.Printf("%d ", score)
		}
	}
	fmt.Println()

	// 4. Concurrency (Goroutines and Channels)
	fmt.Println("Starting async tasks...")
	taskChannel := make(chan string)

	// Launch two concurrent worker threads
	go processTask(1, taskChannel)
	go processTask(2, taskChannel)

	// Receive results from the channel
	fmt.Println(<-taskChannel)
	fmt.Println(<-taskChannel)

	fmt.Println("Program finished successfully!")
}
