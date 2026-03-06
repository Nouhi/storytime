"use client";

import { useState, useRef } from "react";
import type { FamilyMember } from "@/lib/types";

const ROLES = ["mom", "dad", "brother", "sister", "grandma", "grandpa", "aunt", "uncle", "pet", "other"];

interface FamilyMemberCardProps {
  member: FamilyMember;
  onUpdate: (id: number, data: { name?: string; role?: string }) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onUploadPhoto: (memberId: number, file: File) => Promise<void>;
}

export function FamilyMemberCard({
  member,
  onUpdate,
  onDelete,
  onUploadPhoto,
}: FamilyMemberCardProps) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(member.name);
  const [role, setRole] = useState(member.role);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleSave = async () => {
    await onUpdate(member.id, { name, role });
    setEditing(false);
  };

  const handlePhotoClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      await onUploadPhoto(member.id, file);
    }
  };

  return (
    <div className="flex items-center gap-4 p-4 bg-card rounded-xl border border-border hover:border-border/80 transition-colors">
      <button
        onClick={handlePhotoClick}
        className="w-12 h-12 rounded-full bg-muted flex items-center justify-center overflow-hidden ring-2 ring-border hover:ring-primary/40 transition-all cursor-pointer shrink-0"
      >
        {member.photoPath ? (
          <img
            src={`/api/photos/${member.photoPath?.replace("uploads/photos/", "")}`}
            alt={member.name}
            className="w-full h-full object-cover"
          />
        ) : (
          <svg className="w-5 h-5 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <path d="M12 5v14M5 12h14" strokeLinecap="round" />
          </svg>
        )}
      </button>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="hidden"
        onChange={handleFileChange}
      />

      <div className="flex-1 min-w-0">
        {editing ? (
          <div className="flex flex-col gap-2">
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="px-3 py-1.5 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-ring"
              placeholder="Name"
            />
            <select
              value={role}
              onChange={(e) => setRole(e.target.value)}
              className="px-3 py-1.5 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-ring"
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r.charAt(0).toUpperCase() + r.slice(1)}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <>
            <p className="font-medium text-[14px] text-foreground truncate">
              {member.name}
            </p>
            <p className="text-[13px] text-muted-foreground capitalize">
              {member.role}
            </p>
          </>
        )}
      </div>

      <div className="flex gap-1.5 shrink-0">
        {editing ? (
          <>
            <button
              onClick={handleSave}
              className="px-3 py-1.5 text-[13px] font-medium bg-primary text-white rounded-lg hover:bg-primary-hover transition-colors"
            >
              Save
            </button>
            <button
              onClick={() => {
                setEditing(false);
                setName(member.name);
                setRole(member.role);
              }}
              className="px-3 py-1.5 text-[13px] text-muted-foreground hover:text-foreground rounded-lg hover:bg-muted transition-colors"
            >
              Cancel
            </button>
          </>
        ) : (
          <>
            <button
              onClick={() => setEditing(true)}
              className="p-1.5 text-muted-foreground hover:text-foreground rounded-lg hover:bg-muted transition-colors"
              title="Edit"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                <path d="m15 5 4 4" />
              </svg>
            </button>
            <button
              onClick={() => onDelete(member.id)}
              className="p-1.5 text-muted-foreground hover:text-destructive rounded-lg hover:bg-destructive/10 transition-colors"
              title="Remove"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 6h18" />
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
              </svg>
            </button>
          </>
        )}
      </div>
    </div>
  );
}
