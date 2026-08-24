(function(global) {
  'use strict';

  var SVG_NAMESPACE = 'http://www.w3.org/2000/svg';
  var GAIJI_TEXT_COLOR_FILTER_ID = 'hoshi-gaiji-text-color-filter';

  function documentForNode(node) {
    return (node && node.ownerDocument) || global.document || (typeof document !== 'undefined' ? document : null);
  }

  function isGaijiImage(img) {
    return !!(img && img.classList && (
      img.classList.contains('gaiji') ||
      img.classList.contains('gaiji-line') ||
      img.classList.contains('gaiji-wide')
    ));
  }

  function isLargeImage(img) {
    return Number(img && img.naturalWidth || 0) > 256 || Number(img && img.naturalHeight || 0) > 256;
  }

  function appendSvgElement(parent, tagName, attributes) {
    var element = parent.ownerDocument.createElementNS(SVG_NAMESPACE, tagName);
    Object.keys(attributes || {}).forEach(function(name) {
      element.setAttribute(name, attributes[name]);
    });
    parent.appendChild(element);
    return element;
  }

  function ensureGaijiTextColorFilter(doc) {
    if (!doc || !doc.documentElement || !doc.createElementNS) return;
    if (doc.getElementById && doc.getElementById(GAIJI_TEXT_COLOR_FILTER_ID)) return;

    var svg = doc.createElementNS(SVG_NAMESPACE, 'svg');
    svg.setAttribute('width', '0');
    svg.setAttribute('height', '0');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    svg.setAttribute(
      'style',
      'position: absolute !important; width: 0 !important; height: 0 !important; ' +
        'overflow: hidden !important; pointer-events: none !important'
    );

    var filter = appendSvgElement(svg, 'filter', {
      id: GAIJI_TEXT_COLOR_FILTER_ID,
      x: '-10%',
      y: '-10%',
      width: '120%',
      height: '120%',
      'color-interpolation-filters': 'sRGB'
    });
    appendSvgElement(filter, 'feColorMatrix', {
      'in': 'SourceGraphic',
      result: 'inverseLuminance',
      type: 'matrix',
      values: '0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 -0.2126 -0.7152 -0.0722 0 1'
    });
    appendSvgElement(filter, 'feComposite', {
      'in': 'inverseLuminance',
      in2: 'SourceAlpha',
      operator: 'in',
      result: 'glyphMask'
    });
    var componentTransfer = appendSvgElement(filter, 'feComponentTransfer', {
      'in': 'glyphMask',
      result: 'solidStrokeMask'
    });
    appendSvgElement(componentTransfer, 'feFuncA', {
      type: 'linear',
      slope: '1.1'
    });
    appendSvgElement(filter, 'feFlood', {
      result: 'textColor',
      style: 'flood-color: var(--hoshi-text-color)'
    });
    appendSvgElement(filter, 'feComposite', {
      'in': 'textColor',
      in2: 'solidStrokeMask',
      operator: 'in'
    });
    doc.documentElement.appendChild(svg);
  }

  function replaceFailedGaiji(img) {
    if (!isGaijiImage(img) || !img.parentNode) return;
    var doc = documentForNode(img);
    if (!doc || !doc.createElement) return;
    var alt = (img.getAttribute && img.getAttribute('alt')) || '';
    if (!alt.trim()) return;
    var fallback = doc.createElement('span');
    fallback.className = 'hoshi-gaiji-fallback';
    if (Array.from(alt.trim()).length === 1) {
      fallback.classList.add('hoshi-gaiji-fallback-single');
    }
    fallback.setAttribute('data-hoshi-gaiji-alt', alt);
    img.parentNode.insertBefore(fallback, img);
    img.parentNode.removeChild(img);
  }

  function imageSource(img) {
    return (img && (img.currentSrc || img.src || (img.getAttribute && img.getAttribute('src')))) || '';
  }

  function svgImageSource(svgImage) {
    if (!svgImage) return '';
    return svgImage.href && svgImage.href.baseVal
      ? svgImage.href.baseVal
      : ((svgImage.getAttribute && (svgImage.getAttribute('href') || svgImage.getAttribute('xlink:href'))) || '');
  }

  function postImageBridge(src, imageBridge, doc) {
    var bridge = imageBridge || global.HoshiReaderImage;
    if (bridge && bridge.postMessage) {
      bridge.postMessage(new URL(src, doc && doc.baseURI ? doc.baseURI : undefined).href);
    }
  }

  function setupReaderImage(element, src, options) {
    options = options || {};
    if (!element || !src || element.hoshiReaderImageSetup) return;
    element.hoshiReaderImageSetup = true;
    var blurElement = options.blurElement || element;
    if (options.blurImages) {
      blurElement.classList.add('blurred');
      if (options.wrap && !(blurElement.parentElement && blurElement.parentElement.classList.contains('blur-wrapper'))) {
        var doc = documentForNode(blurElement);
        if (doc && doc.createElement && blurElement.parentNode) {
          var target = doc.createElement('span');
          target.className = 'blur-wrapper';
          blurElement.parentNode.insertBefore(target, blurElement);
          target.appendChild(blurElement);
        }
      }
    }
    element.addEventListener('click', function(event) {
      event.preventDefault();
      event.stopPropagation();
      if (blurElement.classList.contains('blurred')) {
        blurElement.classList.remove('blurred');
        return;
      }
      postImageBridge(src, options.imageBridge, documentForNode(element));
    });
  }

  function setupSvgImages(scope, options) {
    var svgImages = Array.from(scope.querySelectorAll ? scope.querySelectorAll('svg image') : []);
    svgImages.forEach(function(svgImage) {
      var svg = svgImage.closest && svgImage.closest('svg');
      if (!svg) return;
      if (svg.getAttribute('preserveAspectRatio') === 'none') {
        svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
      }
      setupReaderImage(svgImage, svgImageSource(svgImage), {
        blurImages: options.blurImages,
        imageBridge: options.imageBridge,
        wrap: false,
        blurElement: svg
      });
    });
  }

  function setupImage(img, options, resolve) {
    var mark = function() {
      if (!isGaijiImage(img) && isLargeImage(img)) {
        img.classList.add('block-img');
        setupReaderImage(img, imageSource(img), {
          blurImages: options.blurImages,
          imageBridge: options.imageBridge,
          wrap: true
        });
      }
      if (resolve) resolve();
    };
    var fail = function() {
      replaceFailedGaiji(img);
      if (resolve) resolve();
    };
    if (img.complete) {
      if ((Number(img.naturalWidth) || 0) > 0) {
        mark();
      } else {
        fail();
      }
      return;
    }
    img.onload = mark;
    img.onerror = fail;
  }

  function setupReaderImages(scope, options) {
    options = options || {};
    scope = scope || global.document || (typeof document !== 'undefined' ? document : null);
    if (!scope || !scope.querySelectorAll) return Promise.resolve();
    ensureGaijiTextColorFilter(documentForNode(scope));
    setupSvgImages(scope, options);
    var images = Array.from(scope.querySelectorAll('img'));
    var waitForImages = options.waitForImages !== false;
    if (!waitForImages) {
      images.forEach(function(img) { setupImage(img, options, null); });
      return Promise.resolve();
    }
    return Promise.all(images.map(function(img) {
      return new Promise(function(resolve) {
        setupImage(img, options, resolve);
      });
    }));
  }

  global.hoshiReaderMediaSemantics = {
    setupReaderImage: setupReaderImage,
    setupReaderImages: setupReaderImages
  };
})(window);
