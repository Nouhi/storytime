"use client";

interface StoryProgressProps {
  step: string;
  detail: string;
  progress: number;
}

const STEP_CONFIG: Record<string, { label: string; icon: string }> = {
  starting: { label: "Getting ready", icon: "sparkle" },
  pending: { label: "Getting ready", icon: "sparkle" },
  "generating-story": { label: "Writing your story", icon: "pencil" },
  "generating-images": { label: "Drawing illustrations", icon: "palette" },
  "assembling-ebook": { label: "Making your ebook", icon: "book" },
  complete: { label: "All done!", icon: "check" },
};

function StepIcon({ icon }: { icon: string }) {
  const cls = "w-4 h-4";
  switch (icon) {
    case "pencil":
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
        </svg>
      );
    case "palette":
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="13.5" cy="6.5" r=".5" fill="currentColor" />
          <circle cx="17.5" cy="10.5" r=".5" fill="currentColor" />
          <circle cx="8.5" cy="7.5" r=".5" fill="currentColor" />
          <circle cx="6.5" cy="12.5" r=".5" fill="currentColor" />
          <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z" />
        </svg>
      );
    case "book":
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
          <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
        </svg>
      );
    default:
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z" />
        </svg>
      );
  }
}

export function StoryProgress({ step, detail, progress }: StoryProgressProps) {
  const config = STEP_CONFIG[step] || { label: step, icon: "sparkle" };

  return (
    <div className="bg-card rounded-2xl border border-border p-6 shadow-sm space-y-5">
      <div className="flex items-center gap-3.5">
        <div className="w-10 h-10 rounded-xl bg-primary-light flex items-center justify-center text-primary">
          <div className="animate-pulse">
            <StepIcon icon={config.icon} />
          </div>
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-medium text-[15px] text-foreground">{config.label}</p>
          <p className="text-sm text-muted-foreground truncate">{detail}</p>
        </div>
        <span className="text-sm font-mono text-muted-foreground tabular-nums">
          {progress}%
        </span>
      </div>

      <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden">
        <div
          className="h-full bg-primary rounded-full transition-all duration-700 ease-out"
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  );
}
