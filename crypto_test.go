package cryptohelper

import (
    "encoding/base64"
    "testing"
)

func TestEncryptReturnsCiphertextForValidKey(t *testing.T) {
    key := []byte("12345678901234567890123456789012")
    plaintext := []byte("hello world")

    encrypted, err := Encrypt(plaintext, key)
    if err != nil {
        t.Fatalf("Encrypt returned error: %v", err)
    }

    if encrypted == "" {
        t.Fatal("Encrypt returned empty ciphertext")
    }
}

func TestDecryptReturnsOriginalPlaintext(t *testing.T) {
    key := []byte("12345678901234567890123456789012")
    plaintext := []byte("hello world")

    encrypted, err := Encrypt(plaintext, key)
    if err != nil {
        t.Fatalf("Encrypt returned error: %v", err)
    }

    decrypted, err := Decrypt(encrypted, key)
    if err != nil {
        t.Fatalf("Decrypt returned error: %v", err)
    }

    if string(decrypted) != string(plaintext) {
        t.Fatalf("got %q want %q", decrypted, plaintext)
    }
}

func TestEncryptRejectsInvalidKeyLength(t *testing.T) {
    _, err := Encrypt([]byte("hello"), []byte("short"))
    if err != ErrInvalidKeyLength {
        t.Fatalf("got %v want %v", err, ErrInvalidKeyLength)
    }
}

func TestDecryptRejectsInvalidKeyLength(t *testing.T) {
    _, err := Decrypt("abcd", []byte("short"))
    if err != ErrInvalidKeyLength {
        t.Fatalf("got %v want %v", err, ErrInvalidKeyLength)
    }
}

func TestDecryptRejectsInvalidBase64(t *testing.T) {
    key := []byte("12345678901234567890123456789012")

    _, err := Decrypt("%%%not-base64%%%", key)
    if err != ErrInvalidCiphertext {
        t.Fatalf("got %v want %v", err, ErrInvalidCiphertext)
    }
}

func TestDecryptRejectsShortCiphertext(t *testing.T) {
    key := []byte("12345678901234567890123456789012")
    shortCiphertext := base64.StdEncoding.EncodeToString([]byte("tiny"))

    _, err := Decrypt(shortCiphertext, key)
    if err != ErrInvalidCiphertext {
        t.Fatalf("got %v want %v", err, ErrInvalidCiphertext)
    }
}

func TestDecryptRejectsTamperedCiphertext(t *testing.T) {
    key := []byte("12345678901234567890123456789012")
    plaintext := []byte("hello world")

    encrypted, err := Encrypt(plaintext, key)
    if err != nil {
        t.Fatalf("Encrypt returned error: %v", err)
    }

    raw, err := base64.StdEncoding.DecodeString(encrypted)
    if err != nil {
        t.Fatalf("base64 decode failed: %v", err)
    }

    raw[len(raw)-1] ^= 0x01
    tampered := base64.StdEncoding.EncodeToString(raw)

    _, err = Decrypt(tampered, key)
    if err != ErrInvalidCiphertext {
        t.Fatalf("got %v want %v", err, ErrInvalidCiphertext)
    }
}

func TestDecryptRejectsWrongKey(t *testing.T) {
    key1 := []byte("12345678901234567890123456789012")
    key2 := []byte("abcdefghijklmnopqrstuvwx12345678")
    plaintext := []byte("hello world")

    encrypted, err := Encrypt(plaintext, key1)
    if err != nil {
        t.Fatalf("Encrypt returned error: %v", err)
    }

    _, err = Decrypt(encrypted, key2)
    if err != ErrInvalidCiphertext {
        t.Fatalf("got %v want %v", err, ErrInvalidCiphertext)
    }
}

func TestEncryptDecryptSupportsEmptyPlaintext(t *testing.T) {
    key := []byte("12345678901234567890123456789012")

    encrypted, err := Encrypt([]byte(""), key)
    if err != nil {
        t.Fatalf("Encrypt returned error: %v", err)
    }

    decrypted, err := Decrypt(encrypted, key)
    if err != nil {
        t.Fatalf("Decrypt returned error: %v", err)
    }

    if string(decrypted) != "" {
        t.Fatalf("got %q want empty string", decrypted)
    }
}
