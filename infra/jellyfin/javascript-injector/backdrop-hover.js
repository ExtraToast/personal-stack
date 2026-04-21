/*
 * Netflix-style page-backdrop reveal on card hover.
 *
 * Paste-target: Jellyfin → Dashboard → Plugins → JavaScript Injector
 *   → Add new script → Name: "backdrop-hover" → paste body, Save.
 *
 * Behaviour: hovering (or keyboard-focusing) a card fades the page
 * background to that card's poster/backdrop image. Crisp — no blur.
 * Cards stay put; only the background changes. Paired with the rest
 * of the Netflix-ish skin in infra/jellyfin/custom.css.
 *
 * Deliberately guarded against double-install so editing the script
 * in the plugin UI while the page is open doesn't stack listeners.
 */
;(function () {
  if (window.__jf_backdrop_hover_installed) return
  window.__jf_backdrop_hover_installed = true

  const BG_OPACITY = 0.45 // how much of the image to show (0 = off, 1 = full)
  const HOVER_DELAY = 120 // ms before the bg swaps (avoids flicker on mouse-travel)
  const FADE_OUT = 400 // ms to fade out when you leave a card

  // Fixed layer behind everything; inherits page black where empty.
  const bg = document.createElement('div')
  bg.id = 'cardHoverBackdrop'
  Object.assign(bg.style, {
    position: 'fixed',
    inset: '0',
    zIndex: '-1',
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundRepeat: 'no-repeat',
    opacity: '0',
    transition: 'opacity ' + FADE_OUT + 'ms ease-out',
    pointerEvents: 'none',
    filter: 'none', // no blur — keep the image sharp
  })
  document.body.appendChild(bg)

  let hoverTimer = null

  function extractImage(card) {
    // Jellyfin usually renders the poster as background-image on
    // .cardImageContainer (or .cardImage for some section templates).
    const ic = card.querySelector('.cardImageContainer, .cardImage')
    if (ic) {
      const b = getComputedStyle(ic).backgroundImage
      if (b && b !== 'none') return b
    }
    // Fallback: any <img> inside the card.
    const img = card.querySelector('img')
    if (img && img.src) return 'url("' + img.src + '")'
    return null
  }

  function showFor(card) {
    const img = extractImage(card)
    if (!img) return
    bg.style.backgroundImage = img
    bg.style.opacity = String(BG_OPACITY)
  }

  function hide() {
    bg.style.opacity = '0'
  }

  document.addEventListener('mouseover', function (e) {
    const card = e.target.closest('.card')
    if (!card) return
    clearTimeout(hoverTimer)
    hoverTimer = setTimeout(function () {
      showFor(card)
    }, HOVER_DELAY)
  }, true)

  document.addEventListener('mouseout', function (e) {
    const card = e.target.closest('.card')
    if (!card) return
    // Ignore moves to descendants of the same card.
    if (card.contains(e.relatedTarget)) return
    clearTimeout(hoverTimer)
    hide()
  }, true)

  document.addEventListener('focusin', function (e) {
    const card = e.target.closest('.card')
    if (card) showFor(card)
  })
  document.addEventListener('focusout', function (e) {
    const card = e.target.closest('.card')
    if (card) hide()
  })
})()
