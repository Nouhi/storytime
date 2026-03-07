"use client";

import { useState, useEffect, useRef } from "react";
import { useSettings } from "@/hooks/use-settings";
import { FamilyMemberCard } from "@/components/family-member-card";
import { StoryPreview } from "@/components/story-preview";
import type { StoryPage } from "@/lib/types";

const READING_LEVELS = [
  { value: "toddler", label: "Toddler (Ages 2-3)" },
  { value: "early-reader", label: "Early Reader (Ages 4-5)" },
  { value: "beginner", label: "Beginner (Ages 6-7)" },
  { value: "intermediate", label: "Intermediate (Ages 8-10)" },
];

const ROLES = ["mom", "dad", "brother", "sister", "grandma", "grandpa", "aunt", "uncle", "pet", "other"];

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function formatCost(cost: number): string {
  if (cost < 0.01) return "<$0.01";
  return `$${cost.toFixed(2)}`;
}

export default function SettingsPage() {
  const {
    settings,
    familyMembers,
    storyHistory,
    loading,
    updateSettings,
    addFamilyMember,
    updateFamilyMember,
    deleteFamilyMember,
    uploadPhoto,
    uploadKidPhoto,
  } = useSettings();

  const [kidName, setKidName] = useState("");
  const [kidGender, setKidGender] = useState("");
  const [readingLevel, setReadingLevel] = useState("early-reader");
  const [anthropicApiKey, setAnthropicApiKey] = useState("");
  const [googleAiApiKey, setGoogleAiApiKey] = useState("");
  const [newName, setNewName] = useState("");
  const [newRole, setNewRole] = useState("brother");
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [saved, setSaved] = useState(false);
  const [modalStory, setModalStory] = useState<{ id: number; pages: StoryPage[]; hasImages: boolean } | null>(null);
  const [modalLoading, setModalLoading] = useState(false);
  const kidPhotoRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (settings) {
      setKidName(settings.kidName);
      setKidGender(settings.kidGender || "");
      setReadingLevel(settings.readingLevel);
      setAnthropicApiKey(settings.anthropicApiKey);
      setGoogleAiApiKey(settings.googleAiApiKey);
    }
  }, [settings]);

  if (loading || !settings) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-muted-foreground text-sm">Loading...</div>
      </div>
    );
  }

  const handleSaveSettings = async () => {
    setSaving(true);
    await updateSettings({ kidName, kidGender, readingLevel, anthropicApiKey, googleAiApiKey });
    setSaving(false);
    setDirty(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const markDirty = () => {
    setDirty(true);
    setSaved(false);
  };

  const handleAddMember = async () => {
    if (!newName.trim()) return;
    await addFamilyMember(newName.trim(), newRole);
    setNewName("");
    setNewRole("brother");
  };

  const handleKidPhotoUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    await uploadKidPhoto(file);
    if (kidPhotoRef.current) kidPhotoRef.current.value = "";
  };

  const handleViewStory = async (storyId: number) => {
    setModalLoading(true);
    try {
      const res = await fetch(`/api/story-history/${storyId}/story-data`);
      if (!res.ok) return;
      const { pages } = await res.json();
      const story = storyHistory.find((s) => s.id === storyId);
      const hasImages = (story?.geminiImageCount ?? 1) > 0;
      setModalStory({ id: storyId, pages, hasImages });
    } catch {
      // fail silently
    } finally {
      setModalLoading(false);
    }
  };

  const inputCls =
    "w-full px-3.5 py-2.5 text-sm rounded-xl border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring transition-shadow";
  const labelCls = "block text-[13px] font-medium text-muted-foreground mb-1.5";

  return (
    <div className="max-w-xl mx-auto space-y-6 pb-12">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-foreground">Settings</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            Configure your story character, family, and API keys.
          </p>
        </div>
        <button
          onClick={handleSaveSettings}
          disabled={!dirty || saving}
          className="px-4 py-2 text-sm font-medium bg-primary text-white rounded-xl hover:bg-primary-hover transition-all disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]"
        >
          {saving ? "Saving..." : saved ? "Saved" : "Save Changes"}
        </button>
      </div>

      {/* Main Character */}
      <section className="bg-card rounded-2xl border border-border p-5 space-y-4 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-primary-light flex items-center justify-center text-primary">
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
            </svg>
          </div>
          <h2 className="text-[15px] font-semibold text-foreground">Main Character</h2>
        </div>

        <div className="grid gap-3">
          <div>
            <label className={labelCls}>Child&apos;s Name</label>
            <input
              type="text"
              value={kidName}
              onChange={(e) => { setKidName(e.target.value); markDirty(); }}
              className={inputCls}
              placeholder="Enter your child's name"
            />
          </div>
          <div>
            <label className={labelCls}>Gender</label>
            <div className="flex gap-2">
              {[
                { value: "boy", label: "Boy" },
                { value: "girl", label: "Girl" },
              ].map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => { setKidGender(option.value); markDirty(); }}
                  className={`px-4 py-2 text-sm font-medium rounded-xl border transition-all ${
                    kidGender === option.value
                      ? "bg-primary text-white border-primary"
                      : "bg-background text-muted-foreground border-border hover:border-primary/40 hover:text-foreground"
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className={labelCls}>Reading Level</label>
            <select
              value={readingLevel}
              onChange={(e) => { setReadingLevel(e.target.value); markDirty(); }}
              className={inputCls}
            >
              {READING_LEVELS.map((level) => (
                <option key={level.value} value={level.value}>
                  {level.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelCls}>Photo</label>
            <p className="text-[12px] text-muted-foreground mb-2">
              Upload a photo so illustrations look like your child.
            </p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => kidPhotoRef.current?.click()}
                className="w-12 h-12 rounded-full bg-muted flex items-center justify-center overflow-hidden ring-2 ring-border hover:ring-primary/40 transition-all cursor-pointer shrink-0"
              >
                {settings.kidPhotoPath ? (
                  /* eslint-disable-next-line @next/next/no-img-element */
                  <img
                    src={`/api/photos/${settings.kidPhotoPath.replace("uploads/photos/", "")}`}
                    alt="Kid photo"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <svg className="w-5 h-5 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <path d="M12 5v14M5 12h14" strokeLinecap="round" />
                  </svg>
                )}
              </button>
              <input
                ref={kidPhotoRef}
                type="file"
                accept="image/jpeg,image/png"
                onChange={handleKidPhotoUpload}
                className="hidden"
              />
              <p className="text-[12px] text-muted-foreground">
                {settings.kidPhotoPath ? "Click photo to change" : "Click to upload"}
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Family Members */}
      <section className="bg-card rounded-2xl border border-border p-5 space-y-4 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-primary-light flex items-center justify-center text-primary">
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
          <h2 className="text-[15px] font-semibold text-foreground">Family Members</h2>
        </div>
        <p className="text-[13px] text-muted-foreground leading-relaxed">
          Upload a photo for each person so illustrations resemble your family. Parents and siblings appear frequently; others make occasional cameos.
        </p>

        {familyMembers.length > 0 && (
          <div className="space-y-2">
            {familyMembers.map((member) => (
              <FamilyMemberCard
                key={member.id}
                member={member}
                onUpdate={updateFamilyMember}
                onDelete={deleteFamilyMember}
                onUploadPhoto={uploadPhoto}
              />
            ))}
          </div>
        )}

        <div className="flex items-end gap-2 pt-1">
          <div className="flex-1">
            <label className={labelCls}>Name</label>
            <input
              type="text"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className={inputCls}
              placeholder="e.g. Sarah"
              onKeyDown={(e) => { if (e.key === "Enter") handleAddMember(); }}
            />
          </div>
          <div className="w-32">
            <label className={labelCls}>Role</label>
            <select
              value={newRole}
              onChange={(e) => setNewRole(e.target.value)}
              className={inputCls}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r.charAt(0).toUpperCase() + r.slice(1)}
                </option>
              ))}
            </select>
          </div>
          <button
            onClick={handleAddMember}
            disabled={!newName.trim()}
            className="px-4 py-2.5 text-sm font-medium bg-primary text-white rounded-xl hover:bg-primary-hover transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Add
          </button>
        </div>
      </section>

      {/* API Keys */}
      <section className="bg-card rounded-2xl border border-border p-5 space-y-4 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-primary-light flex items-center justify-center text-primary">
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4" />
            </svg>
          </div>
          <h2 className="text-[15px] font-semibold text-foreground">API Keys</h2>
        </div>
        <p className="text-[13px] text-muted-foreground leading-relaxed">
          Required for story and illustration generation. Keys are stored locally in your database.
        </p>

        <div className="grid gap-3">
          <div>
            <label className={labelCls}>Anthropic API Key (Claude)</label>
            <input
              type="password"
              value={anthropicApiKey}
              onChange={(e) => { setAnthropicApiKey(e.target.value); markDirty(); }}
              className={`${inputCls} font-mono text-[13px]`}
              placeholder="sk-ant-..."
              autoComplete="off"
            />
          </div>
          <div>
            <label className={labelCls}>Google AI API Key (Gemini)</label>
            <input
              type="password"
              value={googleAiApiKey}
              onChange={(e) => { setGoogleAiApiKey(e.target.value); markDirty(); }}
              className={`${inputCls} font-mono text-[13px]`}
              placeholder="AI..."
              autoComplete="off"
            />
          </div>
        </div>
      </section>

      {/* Story History */}
      <section className="bg-card rounded-2xl border border-border p-5 space-y-4 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-primary-light flex items-center justify-center text-primary">
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 8v4l3 3" />
              <circle cx="12" cy="12" r="10" />
            </svg>
          </div>
          <h2 className="text-[15px] font-semibold text-foreground">Story History</h2>
        </div>

        {storyHistory.length === 0 ? (
          <p className="text-[13px] text-muted-foreground">
            No stories generated yet. Create your first story from the home page!
          </p>
        ) : (
          <div className="space-y-2">
            {storyHistory.map((story) => (
              <div
                key={story.id}
                className="flex items-start justify-between gap-3 py-3 px-3.5 rounded-xl border border-border/60 bg-background"
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">
                    {story.title}
                  </p>
                  <p className="text-[12px] text-muted-foreground mt-0.5">
                    {formatDate(story.createdAt)}
                  </p>
                </div>
                <div className="flex items-center gap-3 flex-shrink-0">
                  {story.pdfPath && (
                    <button
                      onClick={() => handleViewStory(story.id)}
                      disabled={modalLoading}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-[12px] font-medium text-primary bg-primary-light rounded-lg hover:bg-primary/10 transition-colors disabled:opacity-50"
                    >
                      <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
                        <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
                      </svg>
                      View
                    </button>
                  )}
                  <div className="text-right">
                    <p className="text-sm font-medium text-foreground">
                      {formatCost(story.totalCost)}
                    </p>
                    <p className="text-[11px] text-muted-foreground mt-0.5">
                      Claude {formatCost(story.claudeCost)} + Gemini {formatCost(story.geminiCost)}
                    </p>
                  </div>
                </div>
              </div>
            ))}
            {/* Total cost */}
            <div className="flex items-center justify-between pt-2 border-t border-border/40">
              <p className="text-[13px] font-medium text-muted-foreground">
                Total ({storyHistory.length} {storyHistory.length === 1 ? "story" : "stories"})
              </p>
              <p className="text-sm font-semibold text-foreground">
                {formatCost(storyHistory.reduce((sum, s) => sum + s.totalCost, 0))}
              </p>
            </div>
          </div>
        )}
      </section>

      {/* Story preview modal */}
      {modalStory && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
          onClick={(e) => { if (e.target === e.currentTarget) setModalStory(null); }}
        >
          <div className="w-full max-w-4xl rounded-2xl bg-background p-4">
            <StoryPreview
              pages={modalStory.pages}
              storyId={String(modalStory.id)}
              imageBaseUrl={`/api/story-history/${modalStory.id}/pages`}
              epubUrl={`/api/story-history/${modalStory.id}/download`}
              pdfUrl={`/api/story-history/${modalStory.id}/pdf`}
              hasImages={modalStory.hasImages}
            />
          </div>
        </div>
      )}
    </div>
  );
}
