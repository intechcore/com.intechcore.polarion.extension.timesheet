import DOMPurify from 'dompurify';

// The WorkItem cell shows the markup Polarion rendered for the item (icon, linked id, title), which
// the report receives as a string and writes into the DOM. Polarion escapes the values it renders,
// so this is defense in depth: the report keeps its own barrier instead of trusting a string that
// travelled through the REST API, and a title which ever reached it unescaped stays inert here.
//
// The list holds what the renderer emits and nothing more. DOMPurify drops every other tag, every
// event handler attribute and every javascript: URL.
const ALLOWED_TAGS = ['a', 'span', 'div', 'img', 'b', 'i', 'em', 'strong', 'del', 's', 'br'];
const ALLOWED_ATTR = ['href', 'target', 'rel', 'src', 'alt', 'title', 'class', 'style'];

// DOMPurify keeps an allowed `style` attribute as it stands: it does not read the declarations
// inside. Polarion marks the state of an item with a few text properties, so those pass and the
// rest is removed. What this keeps out: `position` and `z-index`, which let a cell cover or
// impersonate the surrounding report, and anything taking a `url()`, which would turn a title into
// an outbound request.
//
// The browser stores a shorthand as its longhands, so `text-decoration: line-through` arrives here
// as text-decoration-line and its siblings. Keeping only some of them would leave the declaration
// half applied, hence the whole group.
const ALLOWED_STYLE_PROPERTIES = new Set([
  'color',
  'font-style',
  'font-weight',
  'text-decoration',
  'text-decoration-color',
  'text-decoration-line',
  'text-decoration-style',
  'text-decoration-thickness',
]);

function stripForeignStyle(element: HTMLElement): void {
  for (const property of Array.from(element.style)) {
    if (!ALLOWED_STYLE_PROPERTIES.has(property)) {
      element.style.removeProperty(property);
    }
  }
  if (element.style.length === 0) {
    element.removeAttribute('style');
  }
}

export function sanitizeWorkItemHtml(html: string): string {
  // A fragment rather than a string, so the declarations are filtered on the sanitized nodes
  // themselves. Reparsing the output would be a second pass over markup already cleared once.
  const fragment = DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    RETURN_DOM_FRAGMENT: true,
  });
  fragment.querySelectorAll<HTMLElement>('[style]').forEach(stripForeignStyle);

  const holder = document.createElement('div');
  holder.append(fragment);
  return holder.innerHTML;
}
