package main

import (
    "crypto/aes"
    "crypto/cipher"
    "crypto/rand"
    "encoding/base64"
    "errors"
    "fmt"
    "io"
)

func encrypt(plaintext string, key []byte) (string, error) {
    if len(key) != 32 {
        return "", errors.New("key must be exactly 32 bytes for AES-256")
    }

    block, err := aes.NewCipher(key)
    if err != nil {
        return "", err
    }

    gcm, err := cipher.NewGCM(block)
    if err != nil {
        return "", err
    }

    nonce := make([]byte, gcm.NonceSize())
    if _, err = io.ReadFull(rand.Reader, nonce); err != nil {
        return "", err
    }

    ciphertext := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
    return base64.StdEncoding.EncodeToString(ciphertext), nil
}

func decrypt(encodedCiphertext string, key []byte) (string, error) {
    if len(key) != 32 {
        return "", errors.New("key must be exactly 32 bytes for AES-256")
    }

    ciphertext, err := base64.StdEncoding.DecodeString(encodedCiphertext)
    if err != nil {
        return "", err
    }

    block, err := aes.NewCipher(key)
    if err != nil {
        return "", err
    }

    gcm, err := cipher.NewGCM(block)
    if err != nil {
        return "", err
    }

    nonceSize := gcm.NonceSize()
    if len(ciphertext) < nonceSize {
        return "", errors.New("ciphertext too short")
    }

    nonce, encrypted := ciphertext[:nonceSize], ciphertext[nonceSize:]
    plaintext, err := gcm.Open(nil, nonce, encrypted, nil)
    if err != nil {
        return "", err
    }

    return string(plaintext), nil
}

func main() {
    key := []byte("12345678901234567890123456789012")
    plainText := "Hello, secure world"

    encrypted, err := encrypt(plainText, key)
    if err != nil {
        panic(err)
    }

    fmt.Println("Encrypted:", encrypted)

    decrypted, err := decrypt(encrypted, key)
    if err != nil {
        panic(err)
    }

    fmt.Println("Decrypted:", decrypted)
}
