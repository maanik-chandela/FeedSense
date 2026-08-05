# FeedSense Data Model (Version 0.1)

## Core Entities

### 1. Research Project
Represents a research study.

Fields:
- Project ID
- Name
- Description
- Platform
- Created Date

Contains:
- Multiple Sessions

---

### 2. Session

Represents one data collection session.

Fields:
- Session ID
- Project ID
- Platform
- Start Time
- End Time
- Duration
- Status
- Notes

Contains:
- Multiple Feed Items

---

### 3. Feed Item

Represents one reel/post/video observed.

Fields:
- Feed Item ID
- Timestamp
- Platform
- Screenshot
- OCR Text
- Caption
- Like Status
- Watch Duration
- AI Classification

---

### 4. OCR Result

Stores extracted text.

Fields:
- OCR ID
- Text
- Language
- Confidence

---

### 5. AI Classification

Stores AI prediction.

Fields:
- Category
- Confidence
- Model Version
- User Corrected
- Final Category

---

### 6. Screenshot

Stores screenshot metadata.

Fields:
- Screenshot ID
- File Path
- Timestamp
- Resolution

---

### 7. Session Statistics

Stores session summary.

Fields:
- Total Feed Items
- Category Distribution
- Average Confidence
- Session Duration

---

### 8. Export Report

Stores exported files.

Fields:
- Export ID
- Format
- Time
- File Path
