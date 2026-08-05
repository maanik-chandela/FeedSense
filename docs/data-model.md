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
