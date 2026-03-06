import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { storyHistory } from "@/lib/db/schema";
import { desc } from "drizzle-orm";

export async function GET() {
  const stories = db
    .select()
    .from(storyHistory)
    .orderBy(desc(storyHistory.createdAt))
    .all();
  return NextResponse.json(stories);
}
