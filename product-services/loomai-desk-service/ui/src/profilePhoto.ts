export async function prepareProfilePhoto(file: File): Promise<string> {
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    throw new Error("Choose a JPG, PNG or WebP image.");
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new Error("Choose an image smaller than 5 MB.");
  }
  let image: ImageBitmap;
  try { image = await createImageBitmap(file); }
  catch { throw new Error("This image could not be read. Choose another photo."); }
  try {
    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 256;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Photo processing is unavailable in this browser.");
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, 256, 256);
    const edge = Math.min(image.width, image.height);
    context.drawImage(image, (image.width - edge) / 2, (image.height - edge) / 2, edge, edge, 0, 0, 256, 256);
    return canvas.toDataURL("image/jpeg", 0.85);
  } finally { image.close(); }
}
