/** Complete the whole batch even when another unlink already removed individual files. */
export async function deleteStoragePrefix(
  bucket: { deleteFiles(options: { prefix: string; force: true }): Promise<void> },
  prefix: string,
): Promise<void> {
  try {
    // The SDK's force option collects per-file errors after processing every file; it does not hide them.
    await bucket.deleteFiles({ prefix, force: true });
  } catch (error) {
    if (Array.isArray(error) && error.length > 0 && error.every((item) => item?.code === 404)) return;
    // List/bucket errors and every non-404 file error must keep the cleanup pending.
    throw error;
  }
}
