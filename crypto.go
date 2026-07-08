package cryptohelper

import (
    "crypto/aes"
    "crypto/cipher"
    "crypto/rand"
    "encoding/base64"
    "errors"
    "io"
)

var (
    ErrInvalidKeyLength = errors.New("key must be exactly 32 bytes for AES-256")
    ErrInvalidCiphertext = errors.New("ciphertext is invalid")
)

func Encrypt(plaintext []byte, key []byte) (string, error) {
    if len(key) != 32 {
        return "", ErrInvalidKeyLength
    }

    block, err := aes.NewCipher(key)
    if err != nil {
        return "", err
    }

    aead, err := cipher.NewGCM(block)
    if err != nil {
        return "", err
    }

    nonce := make([]byte, aead.NonceSize())
    if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
        return "", err
    }

    combined := aead.Seal(nonce, nonce, plaintext, nil)
    return base64.StdEncoding.EncodeToString(combined), nil
}


