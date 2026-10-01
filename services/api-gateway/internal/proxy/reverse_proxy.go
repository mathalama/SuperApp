package proxy

import (
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
)

type ReverseProxyHandler struct {
	target      *url.URL
	stripPrefix string
	proxy       *httputil.ReverseProxy
}

func NewReverseProxy(rawTargetURL string, stripPrefix string) (*ReverseProxyHandler, error) {
	targetURL, err := url.Parse(rawTargetURL)
	if err != nil {
		return nil, err
	}

	proxy := httputil.NewSingleHostReverseProxy(targetURL)
	originalDirector := proxy.Director

	proxy.Director = func(req *http.Request) {
		originalDirector(req)
		req.Host = targetURL.Host

		// Strip prefix if required (e.g. StripPrefix=1 for docs)
		if stripPrefix != "" {
			req.URL.Path = strings.TrimPrefix(req.URL.Path, stripPrefix)
			if !strings.HasPrefix(req.URL.Path, "/") {
				req.URL.Path = "/" + req.URL.Path
			}
		}
	}

	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		log.Printf("[Gateway Proxy Error] %s %s -> %s: %v", r.Method, r.URL.Path, rawTargetURL, err)
		http.Error(w, `{"error":"Bad Gateway: target microservice unavailable"}`, http.StatusBadGateway)
	}

	return &ReverseProxyHandler{
		target:      targetURL,
		stripPrefix: stripPrefix,
		proxy:       proxy,
	}, nil
}

func (h *ReverseProxyHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	h.proxy.ServeHTTP(w, r)
}
