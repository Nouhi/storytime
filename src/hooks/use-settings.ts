"use client";

import { useState, useEffect, useCallback } from "react";
import type { Settings, FamilyMember, StoryHistoryEntry } from "@/lib/types";

export function useSettings() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [familyMembers, setFamilyMembers] = useState<FamilyMember[]>([]);
  const [storyHistory, setStoryHistory] = useState<StoryHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchSettings = useCallback(async () => {
    const [settingsRes, membersRes, historyRes] = await Promise.all([
      fetch("/api/settings"),
      fetch("/api/family-members"),
      fetch("/api/story-history"),
    ]);
    setSettings(await settingsRes.json());
    setFamilyMembers(await membersRes.json());
    setStoryHistory(await historyRes.json());
    setLoading(false);
  }, []);

  useEffect(() => {
    fetchSettings();
  }, [fetchSettings]);

  const updateSettings = async (data: Partial<Settings>) => {
    const res = await fetch("/api/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    setSettings(await res.json());
  };

  const addFamilyMember = async (name: string, role: string) => {
    const res = await fetch("/api/family-members", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, role }),
    });
    const member = await res.json();
    setFamilyMembers((prev) => [...prev, member]);
    return member;
  };

  const updateFamilyMember = async (
    id: number,
    data: { name?: string; role?: string }
  ) => {
    const res = await fetch(`/api/family-members/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    const updated = await res.json();
    setFamilyMembers((prev) => prev.map((m) => (m.id === id ? updated : m)));
  };

  const deleteFamilyMember = async (id: number) => {
    await fetch(`/api/family-members/${id}`, { method: "DELETE" });
    setFamilyMembers((prev) => prev.filter((m) => m.id !== id));
  };

  const uploadPhoto = async (memberId: number, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("memberId", String(memberId));

    const res = await fetch("/api/upload", { method: "POST", body: formData });
    const { photoPath } = await res.json();
    setFamilyMembers((prev) =>
      prev.map((m) => (m.id === memberId ? { ...m, photoPath } : m))
    );
  };

  const uploadKidPhoto = async (file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("type", "kid");

    const res = await fetch("/api/upload", { method: "POST", body: formData });
    const { photoPath } = await res.json();
    setSettings((prev) => prev ? { ...prev, kidPhotoPath: photoPath } : prev);
  };

  return {
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
  };
}
