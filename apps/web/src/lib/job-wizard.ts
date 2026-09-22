import type { SyncJobInput } from "@/lib/types";

export function canContinueJobWizard(step: number, form: SyncJobInput): boolean {
  switch (step) {
    case 0:
      return form.name.trim().length > 0 && form.parallelism > 0 && form.batchSize > 0;
    case 1:
      return Boolean(form.sourceDataSourceId && form.sourceTable);
    case 2:
      return Boolean(form.targetDataSourceId && form.targetTable.trim());
    case 3:
      return true;
    default:
      return false;
  }
}
