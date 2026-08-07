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
