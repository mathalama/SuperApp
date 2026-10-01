package storage

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"image"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"log"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	_ "golang.org/x/image/webp"
)

const (
	MaxDimension = 4096
	MaxFileSize  = 5 * 1024 * 1024 // 5 MB
)

var (
	ErrEmptyFile        = errors.New("avatar file cannot be empty")
	ErrFileTooLarge     = errors.New("Avatar file size exceeds maximum limit of 5MB")
	ErrUnsupportedImage = errors.New("Unsupported image format. Only real JPEG, PNG, and WebP files are allowed.")
)

type AvatarStorage interface {
	UploadAvatar(ctx context.Context, userID uuid.UUID, reader io.Reader, size int64) (string, error)
	DeleteAvatar(ctx context.Context, avatarURL string) error
}

type MinioAvatarStorage struct {
	client     *minio.Client
	bucketName string
	publicURL  string
}

func NewMinioAvatarStorage(
	endpoint, accessKey, secretKey, bucketName, publicURL string,
) (*MinioAvatarStorage, error) {
	// Parse endpoint to extract host and secure flag
	u, err := url.Parse(endpoint)
	var host string
	var useSSL bool
	if err == nil && u.Host != "" {
		host = u.Host
		useSSL = u.Scheme == "https"
	} else {
		host = endpoint
		useSSL = false
	}

	client, err := minio.New(host, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: useSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("failed initializing MinIO client: %w", err)
	}

	storage := &MinioAvatarStorage{
		client:     client,
		bucketName: bucketName,
		publicURL:  strings.TrimRight(publicURL, "/"),
	}

	// Ensure bucket exists
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	exists, err := client.BucketExists(ctx, bucketName)
	if err == nil && !exists {
		log.Printf("[MinIO] Bucket '%s' does not exist, creating...", bucketName)
		if err := client.MakeBucket(ctx, bucketName, minio.MakeBucketOptions{}); err != nil {
			log.Printf("[MinIO WARN] Could not create bucket '%s': %v", bucketName, err)
		} else {
			log.Printf("[MinIO] Bucket '%s' created successfully.", bucketName)
		}
	}

	return storage, nil
}

func (s *MinioAvatarStorage) UploadAvatar(ctx context.Context, userID uuid.UUID, r io.Reader, size int64) (string, error) {
	if size == 0 {
		return "", ErrEmptyFile
	}
	if size > MaxFileSize {
		return "", ErrFileTooLarge
	}

	// Read full content into memory (max 5MB is well within RAM limits)
	buf, err := io.ReadAll(io.LimitReader(r, MaxFileSize+1))
	if err != nil {
		return "", fmt.Errorf("failed reading avatar bytes: %w", err)
	}
	if int64(len(buf)) > MaxFileSize {
		return "", ErrFileTooLarge
	}
	if len(buf) == 0 {
		return "", ErrEmptyFile
	}

	// 1. Verify Magic Numbers
	detectedFormat, contentType, err := DetectImageFormat(buf)
	if err != nil {
		return "", err
	}

	// 2. Validate Dimensions (Pixel Flood Protection)
	if err := ValidateDimensions(buf); err != nil {
		return "", err
	}

	objectName := fmt.Sprintf("%s/avatar-%d.%s", userID.String(), time.Now().UnixMilli(), detectedFormat)

	_, err = s.client.PutObject(ctx, s.bucketName, objectName, bytes.NewReader(buf), int64(len(buf)), minio.PutObjectOptions{
		ContentType: contentType,
	})
	if err != nil {
		return "", fmt.Errorf("failed uploading avatar to MinIO: %w", err)
	}

	avatarURL := fmt.Sprintf("%s/%s/%s", s.publicURL, s.bucketName, objectName)
	log.Printf("[MinIO] Uploaded avatar: user=%s, format=%s, url=%s", userID, detectedFormat, avatarURL)
	return avatarURL, nil
}

func (s *MinioAvatarStorage) DeleteAvatar(ctx context.Context, avatarURL string) error {
	if avatarURL == "" {
		return nil
	}

	prefix := fmt.Sprintf("%s/%s/", s.publicURL, s.bucketName)
	if !strings.HasPrefix(avatarURL, prefix) {
		// URL does not belong to this MinIO bucket
		return nil
	}

	objectName := strings.TrimPrefix(avatarURL, prefix)
	if objectName == "" {
		return nil
	}

	err := s.client.RemoveObject(ctx, s.bucketName, objectName, minio.RemoveObjectOptions{})
	if err != nil {
		log.Printf("[MinIO WARN] Failed deleting avatar %s: %v", objectName, err)
		return err
	}

	log.Printf("[MinIO] Deleted avatar: %s", objectName)
	return nil
}

func DetectImageFormat(data []byte) (format string, contentType string, err error) {
	if len(data) < 12 {
		return "", "", ErrUnsupportedImage
	}

	// JPEG: FF D8 FF
	if data[0] == 0xFF && data[1] == 0xD8 && data[2] == 0xFF {
		return "jpg", "image/jpeg", nil
	}

	// PNG: 89 50 4E 47 0D 0A 1A 0A
	if data[0] == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 &&
		data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A {
		return "png", "image/png", nil
	}

	// WebP: RIFF .... WEBP
	if string(data[0:4]) == "RIFF" && string(data[8:12]) == "WEBP" {
		return "webp", "image/webp", nil
	}

	return "", "", ErrUnsupportedImage
}

func ValidateDimensions(data []byte) error {
	cfg, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		// Could not decode config, format already validated by magic bytes
		return nil
	}

	if cfg.Width > MaxDimension || cfg.Height > MaxDimension {
		return fmt.Errorf("Image dimensions too large (%dx%d). Max allowed is %dx%d px.",
			cfg.Width, cfg.Height, MaxDimension, MaxDimension)
	}

	return nil
}
