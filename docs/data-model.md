# FeedSense Data Model

## 1. Vision
- Mission
- Long-term goals
- Research philosophy

## 2. Core Principles
- Platform independent
- AI assisted
- Research first
- Privacy by design
- Extensible architecture

## 3. Entity Relationship Overview
ResearchProject
 └── Session
      └── FeedItem
           └── AIAnalysis
ResearchProject
 └── ResearchOutput

## 4. Entity Definitions
### ResearchProject
(fields + descriptions)

### Session
(fields + descriptions)

### FeedItem
(fields + descriptions)

### AIAnalysis
(fields + descriptions)

### ResearchOutput
(fields + descriptions)

## 5. AI Pipeline
Screenshot → OCR → Vision Model → Segment Analysis → Topic Detection → Composition → Human Verification

## 6. Future Features
- Recommendation transition graph
- Personalized AI learning
- Cross-platform comparison
- Web dashboard
- Cloud synchronization
- Plugin architecture

## 7. Open Questions
- Multi-modal embeddings
- Privacy-preserving storage
- Federated learning
- Real-time analysis

Date:5/08/2026
- Finalized FeedSense data architecture.
- Separated research entities from configuration.
- Defined AI-assisted labeling workflow.
- Planned implementation of Room Database and MVVM.

## New Design Principles
Never interrupt an active research session.
All uncertain AI predictions are reviewed after the session.
Introduced a dedicated Review Workspace.
AI corrections are stored for future model improvement.
Researcher reviews are performed before export.
## New Entity
ReviewItem
Represents feed items that require human validation.
Stores AI prediction, researcher correction, confidence, and correction reason.
## Future Feature
AI Weakness Report based on accumulated human corrections.

## Implemented Core Entities
ResearchProject
ProjectConfiguration
Session
FeedItem (v1)
AIAnalysis (v1)
ReviewItem

## Architecture Decision
Core entities are implemented before Room Database.
Relationships between entities finalized.

## Future Refactor
Replace string fields representing fixed choices (platform, status, content type, correction reason) with enums for better type safety.

## Design Principle: Configurable over Hardcoded

FeedSense should avoid hardcoded values whenever they represent concepts that may evolve over time.

Examples:
- Platforms
- Categories
- AI Models
- Export Formats
- Review Thresholds

Milestone 5.1 completed
✓ Core data models created.
✓ Entity relationships finalized.
✓ Chose configurable data structures over enums.
✓ Ready to implement Room persistence.

Started Milestone 5.2
- Migrated project to use Room with KSP.
- Adopted modern Android persistence architecture.


ResearchProject stores research metadata instead of only application metadata.

Mandatory:
- Title
- Research Question
- Platform

Optional:
- Description
- Hypothesis
- Owner

Automatically maintained:
- Session Count
- Feed Item Count
- Created At
- Updated At
- Archived

UI Architecture Decision

FeedSense Home Screen manages Research Projects, not Sessions.

Each Session must belong to exactly one Research Project.

A user can create multiple Research Projects.

Each Research Project contains:
- Multiple Sessions
- Multiple Feed Items
- AI Analysis
- Researcher Review
- Exportable Dataset

  Home Screen (v1)

Purpose:
- Display current research project.
- Create a new research project.
- Open previous research projects.
- Continue the active research session.

The Home screen is not responsible for creating or storing observations.
All observations belong to Sessions.
All Sessions belong to exactly one Research Project.

Home Screen Redesign (v1)

The Home screen is a dashboard, not a session manager.

Responsibilities:
- Welcome the researcher.
- Display the active research project.
- Create new research projects.
- Open existing research projects.
- Continue the active research session.

Sessions are always created inside a Research Project.
Research Projects are the top-level entity in FeedSense.

Milestone 5.3 Completed

Research Project Management

Features:
- Persistent Research Projects using Room
- Home Dashboard
- Project Creation
- Projects Listing
- Navigation between Home, Create Project and Projects
- Foundation for Active Project selection

Upcoming:
- Active Project Management
- Background Session Service
- Accessibility Integration

7/08/2026
Session model supports multiple sessions per research project. A researcher may work on any project on any day/pattern and can switch between projects. "Current Project" means the project currently selected/viewed, not a permanently active project. Only one recording session may be RUNNING at a time. Project listings should show latest-session information such as start/end time, duration, session count, and observation count.

## Research Session

A Research Session represents one observation period within a Research Project.

A project can contain multiple sessions. Sessions are independent records and can be created on different days or at different times.

### ResearchSession

- `id` — unique session identifier
- `projectId` — ID of the ResearchProject this session belongs to
- `title` — researcher-defined session title
- `startedAt` — session start timestamp
- `endedAt` — session end timestamp; nullable while active
- `observationCount` — number of observations recorded during the session
- `notes` — optional researcher notes
- `active` — indicates whether the session is currently active

### Session Rules

- A Research Project can have many Research Sessions.
- A researcher can work on different projects on different days.
- Only one Research Session can be active at a time.
- A session can be ended and retained permanently in session history.
- Existing sessions are selected from session history rather than automatically resumed.
- Session start and end times are stored for later analysis.
- Observations will belong to a specific Research Session.

FeedSense Data Model — Progress: Today

Entities implemented:

1. ResearchProject
   - Project-level research container

2. ResearchSession
   - Belongs to ResearchProject
   - Supports active/completed/reopened state
   - Stores session timing
   - Stores observation count

3. ResearchObservation
   - Belongs to ResearchSession
   - Stores manually recorded researcher observations
   - Stores creation timestamp

4. CapturedFrame
   - Belongs to ResearchSession
   - Stores captured image file path
   - Stores capture timestamp
   - Stores analysis status
   - Supports pending-analysis workflow

Database:

FeedSenseDatabase
Version: 4

DAOs:

ProjectDao
SessionDao
ObservationDao
CaptureDao

Repositories:

SessionRepository

Capture pipeline:

MediaProjection
    ↓
ScreenCaptureService
    ↓
ImageReader
    ↓
Bitmap
    ↓
JPEG
    ↓
App-private storage
    ↓
CapturedFrame Room record

Capture protections:

- Frame throttling
- Duplicate-frame detection
- Maximum frame retention
- Session-specific storage
- Latest-image acquisition
- Bitmap/Image cleanup

Analysis groundwork:

CapturedFrame.analysisStatus
    ↓
PENDING
    ↓
FrameAnalysisScheduler
    ↓
Future WorkManager analysis pipeline

Not completed yet:

- Actual AI frame analysis
- Analysis result model
- Analysis worker implementation
- Observation ↔ frame relationship
- Timeline
- Search/filtering
- Export
- Capture-service stop when session ends
- Production hardening

## Research Session & Background Capture Update

### 2026-08-10 / 2026-08-11

### Research Session Implementation

The Research Session data model and persistence flow were extended to support multiple sessions per research project.

A `ResearchSession` belongs to exactly one `ResearchProject` through `projectId`.

Implemented session fields include:

* `id`
* `projectId`
* `title`
* `startedAt`
* `endedAt`
* `observationCount`
* `notes`
* `active`

Session rules:

* A Research Project can contain multiple Research Sessions.
* Sessions are retained after completion.
* Only one recording session should be active at a time.
* Session history is persistent.
* Observations belong to a specific Research Session.
* Captured screen frames belong to a specific Research Session.
* A session can be reopened for another run.

### Research Observations

Research observations are stored separately from captured screen frames.

Each observation belongs to a session through `sessionId`.

The current observation model supports:

* `id`
* `sessionId`
* `text`
* `createdAt`

Observation counts are maintained at the session level.

### Captured Frame Model

A new `CapturedFrame` entity was introduced for storing screenshots collected during an active research session.

Current fields:

* `id`
* `sessionId`
* `filePath`
* `capturedAt`
* `analysisStatus`
* `analysisResult`
* `analyzedAt`

Each captured frame therefore has a complete relationship:

`ResearchProject → ResearchSession → CapturedFrame`

The frame file itself is stored locally, while the database stores its file path and analysis metadata.

### Background Screen Capture

FeedSense now supports background screen capture through Android MediaProjection.

The capture architecture uses:

* `ScreenCaptureService`
* `CaptureManager`
* `MediaProjection`
* `VirtualDisplay`
* `ImageReader`

Screen capture is designed to continue while the researcher is interacting with another application.

The active research session remains the owner of the captured frames.

### Local-First Frame Processing

Captured frames are processed through a background analysis pipeline.

Current pipeline:

`Screen Capture → CapturedFrame → Background Worker → Frame Analyzer → Analysis Result`

The system uses WorkManager for background frame analysis.

Analysis work does not require network connectivity.

This establishes the foundation for the project's privacy-first and budget-conscious AI architecture.

### OCR Analysis

Local OCR has been integrated into the frame analysis pipeline using on-device text recognition.

Current OCR output can identify visible text in captured frames.

The structured analysis result can contain:

* analysis status
* file name
* image dimensions
* file size
* message
* visible text
* screen type
* application
* activity
* confidence

At the current stage, OCR provides actual visible-text extraction while higher-level semantic fields remain available for future vision analysis.

### Local Analysis Foundation

A local frame analyzer has also been established.

The current local analyzer verifies the frame file and extracts basic image information such as:

* width
* height
* file size
* successful local processing status

Higher-level understanding such as application identification, content category, activity, and semantic classification is intentionally reserved for the next AI milestones.

### Frame Analysis States

Captured frames use explicit analysis states:

* `PENDING`
* `PROCESSING`
* `ANALYZED`
* `FAILED`

This allows the background pipeline to distinguish frames waiting for analysis, frames currently being processed, successfully analyzed frames, and failed analysis attempts.

### Human Verification Principle

The data model continues to follow the principle:

> Never interrupt an active research session.

AI predictions that are uncertain should eventually be accumulated for researcher review after the session.

Researcher corrections are intended to become training/feedback data for improving future FeedSense models.

### Milestone 7A Direction — Intelligent Frame Change Detection

The next stage of the background capture system is being designed around local frame-change detection.

The intended architecture is:

`Background Screen Capture → Local Change Detection → Significant Frame → CapturedFrame → Local Analysis`

The change detector should:

* avoid storing identical or nearly identical frames;
* detect meaningful visual changes locally;
* operate continuously during an active session;
* capture short-lived content changes;
* preserve very short feed items that may appear for only a few seconds;
* reduce unnecessary storage and AI/OCR processing;
* avoid requiring cloud AI for basic frame-change detection.

This is intentionally a local preprocessing layer rather than an AI classification layer.

### Long-Term AI Direction

FeedSense will use a hybrid architecture with a strong local-first AI pipeline.

The local system should perform as much processing as practical to control operating costs and preserve privacy.

Cloud AI should be reserved for cases where local analysis is insufficient or where higher-level semantic reasoning is required.

The long-term pipeline is:

`Screen Capture → Local Change Detection → Local OCR/Vision → Content Understanding → Category Classification → Session Data → Human Review`

Ambiguous or low-confidence content should be eligible for later researcher review.

Researcher corrections should become feedback data for improving the local model over time.

### Budget Constraint

The current project direction prioritizes a highly capable local AI system to minimize recurring cloud inference costs.

Target operating cost:

**Maximum target: approximately ₹50 per user per month**

The architecture should therefore prefer:

* on-device processing;
* local frame comparison;
* local OCR;
* local classification where practical;
* batching;
* selective frame persistence;
* selective cloud inference only when necessary.

This constraint is now considered an architectural requirement for future AI milestones.

### Current Data Architecture

The current conceptual data flow is:

`ResearchProject`
→ `ResearchSession`
→ `CapturedFrame`
→ `AIAnalysis`

with researcher observations and future `ReviewItem` records associated with the relevant session/content.

The architecture remains configurable rather than hardcoding evolving concepts such as platforms, categories, AI models, and review thresholds.

### Next Development Stage

The immediate next implementation milestone is **7A: Intelligent Screen-Change Detection**.

The goal is to improve the existing background capture pipeline rather than replace the current session, database, navigation, or analysis architecture.
