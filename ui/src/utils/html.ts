import DOMPurify from 'dompurify';

// The WorkItem cell shows the markup Polarion rendered for the item (icon, linked id, title), which
// the report receives as a string and writes into the DOM. Polarion escapes the values it renders,
// so this is defense in depth: the report keeps its own barrier instead of trusting a string that
// travelled through the REST API, and a title which ever reached it unescaped stays inert here.
//
// The list holds what the renderer emits and nothing more. DOMPurify drops every other tag, every
// event handler attribute and every javascript: URL. `style` stays because Polarion marks a resolved
// item with it.
const ALLOWED_TAGS = ['a', 'span', 'div', 'img', 'b', 'i', 'em', 'strong', 'del', 's', 'br'];
const ALLOWED_ATTR = ['href', 'target', 'rel', 'src', 'alt', 'title', 'class', 'style'];

export function sanitizeWorkItemHtml(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR });
}
