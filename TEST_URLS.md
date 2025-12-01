# Test URLs for Adaptive Streaming

## DASH (Dynamic Adaptive Streaming over HTTP) - .mpd files

### Google Test Streams
```
https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd
https://storage.googleapis.com/shaka-demo-assets/bbb/dash.mpd
https://storage.googleapis.com/shaka-demo-assets/sintel/dash.mpd
```

### Big Buck Bunny (DASH)
```
https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd
https://dash.akamaized.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd
```

### Test Patterns
```
https://test-streams.mux.dev/x36xhzz/x36xhzz.mpd
https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd
```

## HLS (HTTP Live Streaming) - .m3u8 files

### Apple Test Streams
```
https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
```

### Big Buck Bunny (HLS)
```
https://multiplatform-f.akamaihd.net/i/multi/will/bunny/big_buck_bunny_,640x360_400,640x360_700,640x360_1000,950x540_1500,.f4v.csmil/master.m3u8
```

### Sample HLS Streams
```
https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8
https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8
```

## SmoothStreaming - .ism files

### Microsoft Test Streams
```
https://amssamples.streaming.mediaservices.windows.net/683f7e47-bd83-4427-b0a3-26a6c4547782/BigBuckBunny.ism/manifest
https://test.playready.microsoft.com/smoothstreaming/SSWSS720H264/SuperSpeedway_720.ism/Manifest
```

## Regular MP4 (Non-adaptive, but works with current player)

### Google Test Videos
```
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreet.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4
```

## How to Test

### Option 1: Add Test Video to Device
1. Download one of the MP4 files to your device
2. The app will automatically detect it in the gallery
3. Play it to test basic functionality

### Option 2: Test with Adaptive Streams (Recommended)
1. You'll need to modify the app to accept URL input
2. Or add a test button that loads a DASH/HLS URL directly
3. Watch the quality indicator change as network conditions change

### Option 3: Create Test Activity
Create a simple test screen with URL input field to test adaptive streams directly.

## Network Testing Tips

1. **Test WiFi to Cellular Switch:**
   - Start video on WiFi (should use higher quality)
   - Switch to cellular data (should automatically reduce quality)
   - Watch the quality indicator update

2. **Test on Slow Network:**
   - Use Android Emulator network throttling
   - Or use a mobile hotspot with limited bandwidth
   - Quality should adapt to lower bitrates

3. **Monitor Quality Changes:**
   - Watch the quality display (1080p, 720p, 480p, etc.) below the speed button
   - It updates automatically when ExoPlayer switches quality

## ✅ VERIFIED WORKING URLs (Test These First!)

### Regular MP4 (Most Reliable - Start Here):
```
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4
```

### DASH (Adaptive Streaming):
```
https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd
https://test-streams.mux.dev/x36xhzz/x36xhzz.mpd
```

### HLS (Adaptive Streaming):
```
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8
```

## ⚠️ URLs That May Not Work (404 Errors)

Some URLs in the list above may return 404 errors if:
- The server is down
- The content was removed
- Network restrictions block the domain
- CORS/authentication issues

**Solution:** Start with the MP4 URLs above - they are the most reliable.

## Recommended Test URLs (Most Reliable)

### DASH:
```
https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd
```

### HLS:
```
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
```

### Regular MP4 (for basic testing):
```
https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
```

