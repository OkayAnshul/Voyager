# JetBrains Mono Font Setup Guide

## Overview
The Matrix UI redesign uses **JetBrains Mono**, a professional monospace font designed for developers and terminal applications.

## Download Instructions

### Option 1: Google Fonts (Recommended)
1. Visit: https://fonts.google.com/specimen/JetBrains+Mono
2. Click "Download family" button
3. Extract the ZIP file
4. Locate these files in the `static` folder:
   - `JetBrainsMono-Regular.ttf` (400 weight)
   - `JetBrainsMono-Medium.ttf` (500 weight)
   - `JetBrainsMono-Bold.ttf` (700 weight)

### Option 2: Direct Download
```bash
# Download from the JetBrains website
wget https://download.jetbrains.com/fonts/JetBrainsMono-2.304.zip
unzip JetBrainsMono-2.304.zip
cd fonts/ttf
```

## Installation

### Step 1: Rename Font Files
Rename the downloaded files to match Android naming conventions (lowercase, no dashes):

```bash
# In the Voyager project root
cd app/src/main/res/font/

# Rename files (if needed)
mv JetBrainsMono-Regular.ttf jetbrainsmono_regular.ttf
mv JetBrainsMono-Medium.ttf jetbrainsmono_medium.ttf
mv JetBrainsMono-Bold.ttf jetbrainsmono_bold.ttf
```

### Step 2: Place in Font Directory
Copy the renamed files to:
```
app/src/main/res/font/
├── jetbrainsmono_regular.ttf
├── jetbrainsmono_medium.ttf
└── jetbrainsmono_bold.ttf
```

The `font/` directory has been created for you at:
`/home/anshul/AndroidStudioProjects/Voyager/app/src/main/res/font/`

### Step 3: Verify Setup
After adding the fonts:

1. **Build the project** to ensure fonts are recognized:
   ```bash
   ./gradlew build
   ```

2. **Check for errors** - if fonts are missing, the app will fall back to `FontFamily.Monospace` (system monospace font)

3. **Test in app** - open any screen and verify text appears in monospace font

## Fallback Behavior

If font files are not found, the app will automatically fall back to the system monospace font. This is by design in `Type.kt`:

```kotlin
val JetBrainsMonoFontFamily = try {
    FontFamily(
        Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
        Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
        Font(R.font.jetbrainsmono_bold, FontWeight.Bold)
    )
} catch (e: Exception) {
    // Fallback to system monospace if fonts not yet added
    FontFamily.Monospace
}
```

## Verification

To verify fonts are loaded correctly:

1. **Run the app**
2. **Open Developer Options** (tap version 7x in Settings)
3. **Check system logs** for font loading messages
4. **Visual verification**: Text should appear in JetBrains Mono (monospace with distinct characters)

## Troubleshooting

### Build Error: "Unresolved reference: R.font.jetbrainsmono_regular"
**Solution**: Add the font files to `app/src/main/res/font/` directory

### Font looks different than expected
**Solution**:
- Verify you downloaded the correct weights (Regular, Medium, Bold)
- Check file names match exactly: `jetbrainsmono_regular.ttf` (all lowercase, underscore separator)
- Rebuild the project

### App uses system monospace instead of JetBrains Mono
**Cause**: Fonts not found, fallback is active
**Solution**: Follow installation steps above and rebuild

## Font Licenses

JetBrains Mono is licensed under the **OFL (Open Font License)**:
- ✅ Free to use in commercial applications
- ✅ Free to modify and distribute
- ✅ No attribution required (but appreciated)

Full license: https://scripts.sil.org/OFL

## Quick Command Reference

```bash
# Navigate to project root
cd /home/anshul/AndroidStudioProjects/Voyager

# Download fonts (example using wget)
wget https://download.jetbrains.com/fonts/JetBrainsMono-2.304.zip
unzip JetBrainsMono-2.304.zip

# Copy to font directory
cp fonts/ttf/JetBrainsMono-Regular.ttf app/src/main/res/font/jetbrainsmono_regular.ttf
cp fonts/ttf/JetBrainsMono-Medium.ttf app/src/main/res/font/jetbrainsmono_medium.ttf
cp fonts/ttf/JetBrainsMono-Bold.ttf app/src/main/res/font/jetbrainsmono_bold.ttf

# Clean up
rm -rf fonts/ JetBrainsMono-2.304.zip

# Rebuild project
./gradlew clean build
```

## Next Steps

After adding fonts:
1. ✅ Fonts are ready
2. ➡️ Continue with Phase 1.2: Create MatrixComponents.kt library
3. ➡️ Theme system is complete

---

**Status**: Fonts configured in Type.kt - awaiting font file installation
**Fallback**: System monospace (active until fonts added)
**Required Files**: 3 TTF files (Regular, Medium, Bold)
