package storage

import (
	"bytes"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"testing"
)

func TestDetectImageFormat_JPEG(t *testing.T) {
	header := []byte{0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01}
	format, contentType, err := DetectImageFormat(header)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if format != "jpg" || contentType != "image/jpeg" {
		t.Errorf("expected jpg/image/jpeg, got %s/%s", format, contentType)
	}
}

func TestDetectImageFormat_PNG(t *testing.T) {
	header := []byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D}
	format, contentType, err := DetectImageFormat(header)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if format != "png" || contentType != "image/png" {
		t.Errorf("expected png/image/png, got %s/%s", format, contentType)
	}
}

func TestDetectImageFormat_WebP(t *testing.T) {
	header := []byte{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'}
	format, contentType, err := DetectImageFormat(header)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if format != "webp" || contentType != "image/webp" {
		t.Errorf("expected webp/image/webp, got %s/%s", format, contentType)
	}
}

func TestDetectImageFormat_Invalid(t *testing.T) {
	header := []byte("INVALID_BYTES_NOT_IMAGE")
	_, _, err := DetectImageFormat(header)
	if err != ErrUnsupportedImage {
		t.Fatalf("expected ErrUnsupportedImage, got %v", err)
	}
}

func TestValidateDimensions_ValidImage(t *testing.T) {
	img := image.NewRGBA(image.Rect(0, 0, 100, 100))
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		t.Fatalf("encode error: %v", err)
	}

	if err := ValidateDimensions(buf.Bytes()); err != nil {
		t.Errorf("expected valid dimensions, got err: %v", err)
	}
}

func TestValidateDimensions_TooLarge(t *testing.T) {
	img := image.NewRGBA(image.Rect(0, 0, 5000, 100))
	img.Set(0, 0, color.RGBA{R: 255, A: 255})
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, nil); err != nil {
		t.Fatalf("encode error: %v", err)
	}

	err := ValidateDimensions(buf.Bytes())
	if err == nil {
		t.Errorf("expected dimension error for width > 4096, got nil")
	}
}
