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
func Decrypt(encoded string, key []byte) ([]byte, error) {
    if len(key) != 32 {
        return nil, ErrInvalidKeyLength
    }

    combined, err := base64.StdEncoding.DecodeString(encoded)
    if err != nil {
        return nil, ErrInvalidCiphertext
    }

    block, err := aes.NewCipher(key)
    if err != nil {
        return nil, err
    }

    aead, err := cipher.NewGCM(block)
    if err != nil {
        return nil, err
    }

    nonceSize := aead.NonceSize()
    if len(combined) < nonceSize {
        return nil, ErrInvalidCiphertext
    }

    nonce := combined[:nonceSize]
    ciphertext := combined[nonceSize:]

    plaintext, err := aead.Open(nil, nonce, ciphertext, nil)
    if err != nil {
        return nil, ErrInvalidCiphertext
    }

    return plaintext, nil
}


