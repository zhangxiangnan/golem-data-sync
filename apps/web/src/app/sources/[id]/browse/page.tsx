import { DataBrowser } from "@/components/DataBrowser";

export default async function BrowsePage({ params, searchParams }: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ table?: string | string[] }>;
}) {
  const { id } = await params;
  const { table } = await searchParams;
  return <DataBrowser sourceId={id} tableName={typeof table === "string" ? table : undefined} />;
}
