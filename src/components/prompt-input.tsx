"use client";

import { useState, useRef, useEffect } from "react";
import { WRITING_STYLES, IMAGE_STYLES, DEFAULT_WRITING_STYLE, DEFAULT_IMAGE_STYLE } from "@/lib/styles";

interface PromptInputProps {
  onSubmit: (prompt: string, writingStyle: string, imageStyle: string) => void;
  disabled?: boolean;
  kidName?: string;
}

const SUGGESTIONS = [
  { emoji: "🚀", label: "Space adventure", prompt: "a space adventure visiting planets and making alien friends" },
  { emoji: "🌳", label: "Enchanted forest", prompt: "exploring a magical enchanted forest with talking animals" },
  { emoji: "🌊", label: "Under the sea", prompt: "a deep sea submarine adventure discovering underwater treasures" },
  { emoji: "🐉", label: "Dragon quest", prompt: "a quest to befriend a shy baby dragon hiding in the mountains" },
];

export function PromptInput({ onSubmit, disabled, kidName }: PromptInputProps) {
  const [value, setValue] = useState("");
  const [writingStyle, setWritingStyle] = useState(DEFAULT_WRITING_STYLE);
  const [imageStyle, setImageStyle] = useState(DEFAULT_IMAGE_STYLE);
  const [showWritingStyles, setShowWritingStyles] = useState(false);
  const [showImageStyles, setShowImageStyles] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const writingDropdownRef = useRef<HTMLDivElement>(null);
  const imageDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!disabled && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [disabled]);

  // Close dropdowns on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (writingDropdownRef.current && !writingDropdownRef.current.contains(e.target as Node)) {
        setShowWritingStyles(false);
      }
      if (imageDropdownRef.current && !imageDropdownRef.current.contains(e.target as Node)) {
        setShowImageStyles(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSubmit = () => {
    if (value.trim() && !disabled) {
      onSubmit(value.trim(), writingStyle, imageStyle);
      setValue("");
    }
  };

  const handleSuggestion = (prompt: string) => {
    if (!disabled) {
      onSubmit(prompt, writingStyle, imageStyle);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  useEffect(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = "auto";
      textarea.style.height = Math.min(textarea.scrollHeight, 160) + "px";
    }
  }, [value]);

  const placeholder = kidName
    ? `What adventure should ${kidName} go on tonight?`
    : "What should tonight's bedtime story be about?";

  const activeWritingStyle = WRITING_STYLES.find((s) => s.id === writingStyle) || WRITING_STYLES[0];
  const activeImageStyle = IMAGE_STYLES.find((s) => s.id === imageStyle) || IMAGE_STYLES[0];

  return (
    <div className="space-y-3">
      {/* Main input card — light bg, subtle border, rounded like Claude Code desktop */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-[0_1px_8px_rgba(0,0,0,0.06)] transition-shadow focus-within:shadow-[0_2px_16px_rgba(0,0,0,0.1)] focus-within:border-gray-300">
        {/* Textarea area */}
        <div className="px-5 pt-4 pb-2">
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled}
            placeholder={placeholder}
            rows={3}
            className="w-full bg-transparent text-gray-900 placeholder:text-gray-400 resize-none focus:outline-none disabled:opacity-50 text-[15px] leading-relaxed"
          />
        </div>

        {/* Bottom controls bar */}
        <div className="flex items-center justify-between px-3 pb-3 pt-1">
          {/* Style selectors */}
          <div className="flex items-center gap-1.5">
            {/* Writing Style Dropdown */}
            <div className="relative" ref={writingDropdownRef}>
              <button
                onClick={() => { setShowWritingStyles(!showWritingStyles); setShowImageStyles(false); }}
                disabled={disabled}
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-[12px] text-gray-500 hover:text-gray-700 bg-gray-50 hover:bg-gray-100 border border-gray-200 rounded-lg transition-colors disabled:opacity-40"
                title="Writing style"
              >
                <span>{activeWritingStyle.emoji}</span>
                <span className="font-medium">{activeWritingStyle.label}</span>
                <svg className="w-3 h-3 opacity-40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>

              {showWritingStyles && (
                <div className="absolute bottom-full left-0 mb-2 w-72 bg-white border border-gray-200 rounded-xl shadow-xl z-50 overflow-hidden">
                  <div className="px-3 py-2 border-b border-gray-100">
                    <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Writing Style</p>
                  </div>
                  <div className="max-h-64 overflow-y-auto py-1">
                    {WRITING_STYLES.map((style) => (
                      <button
                        key={style.id}
                        onClick={() => { setWritingStyle(style.id); setShowWritingStyles(false); }}
                        className={`w-full flex items-start gap-2.5 px-3 py-2.5 text-left transition-colors ${
                          writingStyle === style.id
                            ? "bg-orange-50 text-gray-900"
                            : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                        }`}
                      >
                        <span className="text-base mt-0.5 flex-shrink-0">{style.emoji}</span>
                        <div className="min-w-0">
                          <p className="text-[13px] font-medium">{style.label}</p>
                          <p className="text-[11px] text-gray-400 mt-0.5">{style.description}</p>
                        </div>
                        {writingStyle === style.id && (
                          <svg className="w-4 h-4 text-orange-500 flex-shrink-0 mt-0.5 ml-auto" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                            <polyline points="20 6 9 17 4 12" />
                          </svg>
                        )}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Image Style Dropdown */}
            <div className="relative" ref={imageDropdownRef}>
              <button
                onClick={() => { setShowImageStyles(!showImageStyles); setShowWritingStyles(false); }}
                disabled={disabled}
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-[12px] text-gray-500 hover:text-gray-700 bg-gray-50 hover:bg-gray-100 border border-gray-200 rounded-lg transition-colors disabled:opacity-40"
                title="Image style"
              >
                <span>{activeImageStyle.emoji}</span>
                <span className="font-medium">{activeImageStyle.label}</span>
                <svg className="w-3 h-3 opacity-40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>

              {showImageStyles && (
                <div className="absolute bottom-full left-0 mb-2 w-72 bg-white border border-gray-200 rounded-xl shadow-xl z-50 overflow-hidden">
                  <div className="px-3 py-2 border-b border-gray-100">
                    <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Image Style</p>
                  </div>
                  <div className="max-h-64 overflow-y-auto py-1">
                    {IMAGE_STYLES.map((style) => (
                      <button
                        key={style.id}
                        onClick={() => { setImageStyle(style.id); setShowImageStyles(false); }}
                        className={`w-full flex items-start gap-2.5 px-3 py-2.5 text-left transition-colors ${
                          imageStyle === style.id
                            ? "bg-orange-50 text-gray-900"
                            : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                        }`}
                      >
                        <span className="text-base mt-0.5 flex-shrink-0">{style.emoji}</span>
                        <div className="min-w-0">
                          <p className="text-[13px] font-medium">{style.label}</p>
                          <p className="text-[11px] text-gray-400 mt-0.5">{style.description}</p>
                        </div>
                        {imageStyle === style.id && (
                          <svg className="w-4 h-4 text-orange-500 flex-shrink-0 mt-0.5 ml-auto" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                            <polyline points="20 6 9 17 4 12" />
                          </svg>
                        )}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Send button — warm orange like Claude Code */}
          <button
            onClick={handleSubmit}
            disabled={!value.trim() || disabled}
            className="w-8 h-8 flex items-center justify-center bg-[#c67a4a] text-white rounded-lg transition-all hover:bg-[#b56a3a] active:scale-95 disabled:opacity-20 disabled:cursor-not-allowed"
            aria-label="Send"
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="m5 12 7-7 7 7" />
              <path d="M12 19V5" />
            </svg>
          </button>
        </div>
      </div>

      {/* Suggestion chips */}
      <div className="flex flex-wrap items-center justify-center gap-2">
        {SUGGESTIONS.map((s) => (
          <button
            key={s.label}
            onClick={() => handleSuggestion(s.prompt)}
            disabled={disabled}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 text-[13px] text-gray-500 bg-white border border-gray-200 rounded-full hover:border-gray-300 hover:text-gray-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed shadow-sm"
          >
            <span>{s.emoji}</span>
            {s.label}
          </button>
        ))}
      </div>
    </div>
  );
}
