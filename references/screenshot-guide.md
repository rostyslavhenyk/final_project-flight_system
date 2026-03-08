# Screenshot Guide for Evidence Collection

**COMP2850 HCI - Privacy-Safe Screenshot Practices**

---

## Why Screenshots Matter

For Task 1 (Week 9) and Task 2 (Week 10-11) submissions, you'll include **screenshot evidence** showing:
- Interface states (before/after redesign)
- Accessibility features (ARIA live regions, focus indicators)
- Browser DevTools inspections (HTML structure, network timing)
- Screen reader output (NVDA/VoiceOver speech viewer)

Screenshots must be **privacy-safe** (no PII) and **readable** (clear, well-cropped).

---

## 1. Recommended Tools

### Windows

| Tool | Use Case | How to Access |
|------|----------|---------------|
| **Snipping Tool** | Quick screenshots, built-in | `Win + Shift + S` |
| **Windows Snip & Sketch** | Annotate after capture | Windows Search → "Snip & Sketch" |
| **ShareX** (free) | Advanced (regions, scrolling pages) | Download: https://getsharex.com/ |
| **Greenshot** (free) | Annotate, auto-save to folders | Download: https://getgreenshot.org/ |

### macOS

| Tool | Use Case | How to Access |
|------|----------|---------------|
| **Screenshot** (built-in) | Full screen, window, region | `Cmd + Shift + 3/4/5` |
| **CleanShot X** (paid) | Professional annotations | App Store |
| **Skitch** (free) | Annotate after capture | App Store |

### Linux

| Tool | Use Case | How to Access |
|------|----------|---------------|
| **Spectacle** (KDE) | Full screen, window, region | Install via package manager |
| **GNOME Screenshot** | Built-in for GNOME desktop | `PrtScn` key |
| **Flameshot** (free) | Annotate, draw arrows | Install: `sudo apt install flameshot` |

### Browser Extensions (Cross-Platform)

| Extension | Use Case | Install |
|-----------|----------|---------|
| **Awesome Screenshot** | Full page, scrolling captures | Chrome/Firefox Web Store |
| **Nimbus Screenshot** | Annotate, blur sensitive areas | Chrome/Firefox Web Store |
| **Full Page Screen Capture** | Long pages (useful for task lists) | Chrome Web Store |

---

## 2. What to Crop Out (Privacy Scrubbing)

### ❌ Remove These (PII/Sensitive Data)

- **Usernames/Real Names**: In browser tabs, bookmarks, login forms
- **Email Addresses**: In bookmarks, open Gmail tabs, login screens
- **Profile Pictures**: In browser toolbar, Google account icons
- **Bookmark Bar Content**: May reveal personal sites, work projects
- **Browser History**: If visible in search suggestions
- **File Paths with Usernames**: `C:\Users\JohnSmith\...` → `C:\Users\[redacted]\...`
- **IP Addresses**: In DevTools Network tab (if showing server IPs)
- **Session Tokens**: In DevTools Application → Cookies
- **Real Task Data**: If using personal tasks instead of test data

### ✅ Keep These (Useful Evidence)

- **Browser Name/Version**: "Chrome 120.0" (shows compatibility)
- **Window Title**: "Task Manager - localhost:8080" (shows page context)
- **DevTools Panel**: HTML inspector, Console, Network tab
- **Code Line Numbers**: In DevTools Elements panel
- **ARIA Attributes**: `role="status"`, `aria-label="..."`
- **Network Request Timing**: Duration, status codes (200, 400)
- **Screen Reader Output**: NVDA/VoiceOver speech viewer text

---

## 3. Screenshot Checklist (Before Saving)

Before saving each screenshot, check:

- [ ] **No real names** in browser tabs, bookmarks, or login forms
- [ ] **No email addresses** visible
- [ ] **No personal task titles** (use test data: "Buy milk", "Pay bills")
- [ ] **No usernames in file paths** (crop or blur `C:\Users\YourName\`)
- [ ] **Bookmark bar hidden or cropped**
- [ ] **Screenshot is readable** (text legible, not too small)
- [ ] **Relevant content in focus** (crop to show only what's needed)
- [ ] **Annotations added** (if needed: arrows, boxes, labels)

---

## 4. Optimal Dimensions & File Formats

### Dimensions

**For web interfaces**:
- **Full screenshot**: 1920×1080 (or your screen resolution)
- **Cropped to browser content**: 1280×720 (readable in PDFs)
- **Close-up (DevTools, code)**: 800×600 minimum (text must be legible)

**General rule**: Text should be **14pt or larger** when viewed at 100% zoom in PDF.

### File Formats

| Format | When to Use | Pros | Cons |
|--------|-------------|------|------|
| **PNG** | Screenshots with text, UI elements | Lossless, sharp text | Larger file size |
| **JPG** | Photos, complex images (not UI) | Smaller file size | Lossy compression, blurry text |
| **WebP** | Modern alternative to PNG/JPG | Smaller + lossless | Not all viewers support it |

**Recommendation**: Use **PNG** for all UI/code screenshots. Use **JPG** only for photos (if applicable).

---

## 5. How to Crop Effectively

### Tool-Specific Instructions

#### Windows Snipping Tool
1. Press `Win + Shift + S`
2. Drag rectangle around **relevant area only** (not entire screen)
3. Click notification → Opens Snip & Sketch
4. **Before saving**: Use pen tool to blur usernames/emails
5. Save as PNG to `wk09/evidence/screenshots/`

#### macOS Screenshot
1. Press `Cmd + Shift + 4`
2. Drag crosshair around relevant area
3. Screenshot saves to Desktop (default)
4. Open in Preview → Tools → Annotate → Blur sensitive areas
5. Export as PNG to `wk09/evidence/screenshots/`

#### Linux (Flameshot)
1. Run `flameshot gui`
2. Drag rectangle around relevant area
3. Use blur tool (icon) to redact sensitive info
4. Click Save icon → Save as PNG

---

## 6. Common Screenshot Types & Best Practices

### A. Interface Screenshots (Full Page)

**Purpose**: Show complete UI state (e.g., task list before/after redesign)

**Best Practices**:
- Hide bookmark bar (`Ctrl + Shift + B` in most browsers)
- Close unnecessary tabs (only keep relevant tab open)
- Zoom browser to 100% (not 125% or 67%)
- Ensure URL bar shows `localhost:8080` or Codespaces URL
- Crop to show browser content area + minimal chrome

**Example Filename**: `task-list-before-redesign.png`

---

### B. DevTools Screenshots (HTML Structure)

**Purpose**: Show ARIA attributes, semantic HTML, live regions

**Best Practices**:
- Open DevTools (`F12`)
- Navigate to Elements tab
- Expand relevant HTML section (e.g., `<div id="status">`)
- Highlight element to show in right panel:
  - Attributes (ARIA roles, labels)
  - Styles
  - Accessibility tree (if available)
- **Crop tightly** to code + right panel (no need for full browser window)
- Increase DevTools font size if text is small (DevTools Settings → Appearance)

**Example Filename**: `aria-live-region-devtools.png`

---

### C. Screen Reader Screenshots (Speech Viewer)

**Purpose**: Show what screen reader announces

**NVDA (Windows)**:
1. Enable Speech Viewer: `NVDA menu → Tools → Speech Viewer`
2. Navigate through interface with `Tab` or `Down Arrow`
3. Speech Viewer window shows announced text
4. Screenshot Speech Viewer window (crop to just that window)
5. **Blur any personal info** in background windows

**VoiceOver (macOS)**:
1. Enable Caption Panel: VoiceOver Utility → Visuals → Show Caption Panel
2. Navigate with `VO + Right Arrow` (where VO = `Ctrl + Option`)
3. Caption Panel shows announced text
4. Screenshot Caption Panel
5. **Blur any personal info** in background

**Example Filename**: `nvda-task-added-announcement.png`

---

### D. Network Timing Screenshots (Performance Evidence)

**Purpose**: Show request duration (for Task 1 metrics analysis)

**Best Practices**:
- Open DevTools (`F12`) → Network tab
- Perform action (e.g., add task)
- Look for POST request to `/tasks`
- Click request → Timing tab shows duration
- **Crop to show**:
  - Request URL (`/tasks`)
  - Status code (200, 400, etc.)
  - **Duration** (e.g., `234ms`)
  - Request headers showing `HX-Request: true` (if HTMX mode)
- **Crop out**:
  - Session cookies (in Cookies tab)
  - Other unrelated requests

**Example Filename**: `add-task-timing-234ms.png`

---

## 7. Organizing Screenshots for Submission

### Directory Structure

```
wk09/evidence/screenshots/
├── 01-baseline/
│   ├── task-list-full-page.png
│   ├── add-form-validation-error.png
│   └── aria-live-region-devtools.png
├── 02-screen-reader/
│   ├── nvda-task-added.png
│   ├── nvda-validation-error.png
│   └── voiceover-delete-confirmation.png
├── 03-network-timing/
│   ├── add-task-timing.png
│   ├── delete-task-timing.png
│   └── filter-task-timing.png
└── 04-after-redesign/
    ├── improved-error-message.png
    ├── aria-alert-devtools.png
    └── nvda-improved-announcement.png
```

### Naming Convention

Use **descriptive, searchable filenames**:

**Good** ✅:
- `task-list-before-redesign.png`
- `nvda-announcement-validation-error.png`
- `devtools-aria-live-region-status.png`
- `network-timing-add-task-567ms.png`

**Bad** ❌:
- `Screenshot1.png`
- `IMG_2024_10_14.png`
- `Capture.png`
- `untitled.png`

---

## 8. Privacy Scrubbing Tools

### Built-In (Most Tools)

- **Blur Tool**: Flameshot, Greenshot, Snip & Sketch
- **Rectangle/Box**: Draw solid box over sensitive area (use same color as background)
- **Crop**: Remove edges with sensitive info

### External (If Needed)

- **ImageMagick** (command-line):
  ```bash
  # Blur region (x, y, width, height)
  convert input.png -region 100x50+10+10 -blur 0x8 output.png
  ```

- **GIMP** (free GUI editor):
  1. Open image
  2. Select region (Rectangle Select tool)
  3. Filters → Blur → Pixelize (or Gaussian Blur)
  4. Export as PNG

### Quick Check: Metadata Removal

Some screenshot tools embed metadata (creation date, device name). Remove with:

**ExifTool** (all platforms):
```bash
exiftool -all= screenshot.png
```

**Or**: Most screenshot tools don't embed EXIF data by default (unlike phone cameras), but verify if submitting to external sites.

---

## 9. Annotation Best Practices

When adding **arrows, boxes, or text** to explain screenshots:

### Good Annotations ✅

- **Red boxes/arrows**: Highlight relevant code/UI element
- **Text labels**: Short (1-3 words), clear font (Arial 14pt+)
- **Contrast**: Red/yellow on light backgrounds, white/yellow on dark
- **Purpose**: Point to specific ARIA attribute, error message, focus indicator

**Example**: Red arrow pointing to `role="status"` in DevTools with label "Live region"

### Bad Annotations ❌

- **Too many arrows**: Cluttered, confusing
- **Tiny text**: Labels must be 14pt+ to read in PDF
- **Vague labels**: "This part" instead of "ARIA live region"
- **Covering content**: Arrow/box blocks the code you're highlighting

---

## 10. Testing Screenshot Readability

Before submitting, test if screenshots are readable:

1. **Export to PDF** (as you will for submission)
2. **View at 100% zoom** (not zoomed in)
3. **Check text is legible**:
   - Code in DevTools should be readable without squinting
   - ARIA attributes should be clear
   - Error messages should be sharp (not blurry JPG artifacts)
4. **If too small**: Retake at higher resolution or crop tighter

**Rule of thumb**: If you need to zoom to 150%+ to read text, screenshot is too small/blurry.

---

## 11. Common Mistakes & Fixes

### Mistake 1: Full Desktop Screenshot (Too Much Context)

**Problem**: Screenshot shows taskbar, desktop icons, unrelated windows
**Fix**: Crop to **browser window only** or **DevTools panel only**

---

### Mistake 2: Text Too Small (Low Resolution)

**Problem**: Took screenshot on 4K monitor but text is tiny in PDF
**Fix**: Increase browser zoom to 125-150% before screenshot, OR use higher DPI export

---

### Mistake 3: Personal Bookmarks Visible

**Problem**: Bookmark bar shows "Personal Email", "Work Project", "Bank Login"
**Fix**: Hide bookmark bar (`Ctrl + Shift + B`) OR crop it out OR blur it

---

### Mistake 4: Username in File Path

**Problem**: Screenshot shows `C:\Users\JohnSmith\IdeaProjects\comp2850\...`
**Fix**:
- Option A: Crop file path out
- Option B: Blur username portion
- Option C: Use environment variable in terminal: `~/IdeaProjects/...` instead of full path

---

### Mistake 5: Dark Mode Code Unreadable

**Problem**: White text on black background exports as low-contrast gray in PDF
**Fix**:
- Use **light theme** in DevTools (Settings → Appearance → Light)
- OR export as PNG (not JPG which loses contrast)

---

## 12. Accessibility of Screenshots (For Your Portfolio)

When preparing your **final portfolio**, ensure screenshots are accessible:

### Add Alt Text (If Embedding in Web Portfolio)

```html
<img src="aria-live-region.png"
     alt="Chrome DevTools showing div element with id='status', role='status', and aria-live='polite' attributes">
```

### Provide Captions (In PDF Submission)

**Example**:
```
Figure 1: ARIA live region in DevTools
The status div has role="status" and aria-live="polite", ensuring screen readers announce task additions without interrupting the user.
```

### Use High Contrast

- Avoid light gray text on white backgrounds
- Annotations should be **red** (high contrast) not light pink
- Ensure focus indicators are visible (3:1 contrast minimum)

---

## 13. Quick Reference: Screenshot Workflow

1. **Before**: Hide bookmark bar, close extra tabs, use test data
2. **Capture**: Use tool's region select (not full screen)
3. **Inspect**: Check for PII (usernames, emails, file paths)
4. **Scrub**: Blur or crop sensitive areas
5. **Annotate** (if needed): Add red arrows/boxes to highlight
6. **Save**: PNG format, descriptive filename
7. **Organize**: Move to appropriate evidence subfolder
8. **Verify**: Open in PDF viewer at 100% zoom, check readability

---

## 14. Example Evidence Package

Here's what a complete screenshot evidence folder might look like for **Task 1 (Week 9)**:

```
wk09/evidence/screenshots/
├── 01-baseline-interface/
│   ├── task-list-full-view.png              (1280x720, 156KB)
│   ├── add-task-form-validation-error.png   (800x600, 89KB)
│   └── filter-results-no-announcement.png   (1280x720, 142KB)
│
├── 02-html-structure/
│   ├── aria-live-region-status-devtools.png (1024x768, 203KB)
│   ├── form-labels-aria-describedby.png     (800x600, 134KB)
│   └── skip-link-html-structure.png         (800x600, 98KB)
│
├── 03-screen-reader-testing/
│   ├── nvda-task-added-success.png          (600x400, 45KB)
│   ├── nvda-validation-error-not-announced.png (600x400, 52KB)
│   └── voiceover-delete-button-label.png    (700x300, 67KB)
│
├── 04-network-performance/
│   ├── add-task-post-timing-234ms.png       (900x700, 178KB)
│   ├── delete-task-post-timing-187ms.png    (900x700, 165KB)
│   └── filter-get-timing-1847ms.png         (900x700, 189KB)
│
└── 05-after-redesign/
    ├── improved-error-aria-alert.png        (1280x720, 156KB)
    ├── nvda-now-announces-error.png         (600x400, 48KB)
    └── devtools-role-alert-added.png        (800x600, 145KB)
```

**Total**: ~20 screenshots, ~2MB compressed (well within Gradescope limits)

---

## Resources

- **WCAG 2.2 Images of Text**: https://www.w3.org/WAI/WCAG22/Understanding/images-of-text
- **UK GDPR Compliance**: https://ico.org.uk/for-organisations/guide-to-data-protection/
- **Screenshot Tools Comparison**: https://alternativeto.net/software/snipping-tool/

---

**Guide Version**: 1.0
**Last Updated**: 2025-10-14
**Module**: COMP2850 HCI
**Contact**: See module Minerva page for help with evidence submission
