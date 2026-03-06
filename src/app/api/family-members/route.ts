import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { familyMembers } from "@/lib/db/schema";

export async function GET() {
  const members = db.select().from(familyMembers).all();
  return NextResponse.json(members);
}

export async function POST(request: Request) {
  const body = await request.json();
  const { name, role } = body;

  const result = db
    .insert(familyMembers)
    .values({ name, role })
    .returning()
    .get();

  return NextResponse.json(result, { status: 201 });
}
