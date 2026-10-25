/*
   xBRLv Quality (8 samples)
   PPSSPP / OpenGL ES
   
   - Upscale inteligente
   - Detecção de bordas
   - 8 amostras
   - Prioridade total em qualidade
   - Curvas suaves (smoothstep) no lugar de serrilhado
   
   Base: Hyllian xBR
*/

#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump int;
#endif

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;

varying vec2 v_texcoord0;

// ============================================================
// === AJUSTES (mexa aqui) ====================================
// ============================================================

// --- Qualidade xBR ---
#define XBR_Y_WEIGHT         48.0
#define XBR_EQ_THRESHOLD     12.0
#define XBR_LV2_COEFFICIENT   2.0
#define CURVE_SOFTNESS        0.45   // maior = curvas mais suaves

// --- Cor ---
#define BRIGHTNESS            1.10   // 1.0 = neutro | >1 mais claro | <1 mais escuro
#define CONTRAST              1.15   // 1.0 = neutro | >1 mais contraste
#define SATURATION            1.20   // 1.0 = neutro | >1 mais saturado | 0 = P&B
#define GAMMA                 1.06   // 1.0 = neutro | <1 clareia sombras | >1 escurece

// --- LCD / Opacidade ---
// Grade LCD: 1 quadradinho por pixel do jogo, linhas finas escuras
// 0.0 = desligado | 0.15~0.30 = sutil | >0.40 = forte
#define LCD_OPACITY           0.20
// Espessura da linha em pixels da TELA (1.0 = fina | 1.5 = media)
#define LCD_LINE_WIDTH        1.0

// ============================================================

// OTIMIZ: mediump (16 bits) nas cores/luminancia/distancias; coordenadas e curvas seguem em precisao alta
const mediump vec3 Y = vec3(0.299, 0.587, 0.114);

mediump vec4 df(mediump vec4 A, mediump vec4 B) {
    return abs(A - B);
}

bvec4 and4(bvec4 A, bvec4 B) {
    return bvec4(A.x && B.x, A.y && B.y, A.z && B.z, A.w && B.w);
}

mediump vec4 weighted_distance(mediump vec4 a, mediump vec4 b, mediump vec4 c, mediump vec4 d, mediump vec4 e, mediump vec4 f, mediump vec4 g, mediump vec4 h) {
    return (df(a,b) + df(a,c) + df(d,e) + df(d,f) + 4.0 * df(g,h));
}

vec3 color_adjust(vec3 color) {
    // Gamma (OTIMIZ: pula o pow quando GAMMA = 1.0, pois nao muda nada)
    if (abs(GAMMA - 1.0) > 0.001) {
        color = pow(max(color, vec3(0.0)), vec3(1.0 / max(GAMMA, 0.01)));
    }

    // Brightness
    color *= BRIGHTNESS;

    // Contrast
    color = (color - 0.5) * CONTRAST + 0.5;

    // Saturation
    float luma = dot(color, Y);
    color = mix(vec3(luma), color, SATURATION);

    return clamp(color, 0.0, 1.0);
}

void main() {
    bool do_upscale = u_texelDelta.x > (1.45 * u_pixelDelta.x);
    mediump vec3 res = texture2D(sampler0, v_texcoord0).rgb;

    if (do_upscale) {
        vec2 ps = 1.0 / u_texelDelta;
        vec2 fp = fract(v_texcoord0 * ps);

        vec2 dx = vec2(u_texelDelta.x, 0.0);
        vec2 dy = vec2(0.0, u_texelDelta.y);

        // === 8 amostras (3x3 sem o canto superior-esquerdo) ===
        mediump vec3 B = texture2D(sampler0, v_texcoord0      - dy).rgb;
        mediump vec3 C = texture2D(sampler0, v_texcoord0 + dx - dy).rgb;
        mediump vec3 D = texture2D(sampler0, v_texcoord0 - dx     ).rgb;
        mediump vec3 E = res; // OTIMIZ: reaproveita a amostra central ja lida (1 fetch a menos)
        mediump vec3 F = texture2D(sampler0, v_texcoord0 + dx     ).rgb;
        mediump vec3 G = texture2D(sampler0, v_texcoord0 - dx + dy).rgb;
        mediump vec3 H = texture2D(sampler0, v_texcoord0 + dy     ).rgb;
        mediump vec3 I = texture2D(sampler0, v_texcoord0 + dx + dy).rgb;

        // Aprox. do pixel A (canto superior-esquerdo) usando B
        mediump vec3 A = B;

        // Luminância
        mediump vec4 b = vec4(dot(B, Y), dot(D, Y), dot(H, Y), dot(F, Y)) * XBR_Y_WEIGHT;
        mediump vec4 c = vec4(dot(C, Y), dot(A, Y), dot(G, Y), dot(I, Y)) * XBR_Y_WEIGHT;
        mediump vec4 e = vec4(dot(E, Y)) * XBR_Y_WEIGHT;

        // OTIMIZ: area totalmente plana (8 vizinhos com a mesma luminancia de E).
        // Nesse caso o xBR nunca acha borda e o resultado e sempre E, entao pula tudo.
        // Resultado identico ao original, so evita calculo inutil.
        bool is_flat_area = all(equal(b, e)) && all(equal(c, e));

        if (!is_flat_area) {
            mediump vec4 d = b.yzwx;
            mediump vec4 f = b.wxyz;
            mediump vec4 g = c.zwxy;
            mediump vec4 h = b.zwxy;
            mediump vec4 i = c.wxyz;

            // Linhas para curvas
            vec4 Ao = vec4( 1.0, -1.0, -1.0,  1.0);
            vec4 Bo = vec4( 1.0,  1.0, -1.0, -1.0);
            vec4 Co = vec4( 1.5,  0.5, -0.5,  0.5);
            vec4 Ax = vec4( 1.0, -1.0, -1.0,  1.0);
            vec4 Bx = vec4( 0.5,  2.0, -0.5, -2.0);
            vec4 Cx = vec4( 1.0,  1.0, -0.5,  0.0);
            vec4 Ay = vec4( 1.0, -1.0, -1.0,  1.0);
            vec4 By = vec4( 2.0,  0.5, -2.0, -0.5);
            vec4 Cy = vec4( 2.0,  0.0, -1.0,  0.5);

            vec4 fx      = Ao * fp.y + Bo * fp.x;
            vec4 fx_left = Ax * fp.y + Bx * fp.x;
            vec4 fx_up   = Ay * fp.y + By * fp.x;

            // Restrições de interpolação
            bvec4 interp_restriction_lv1 = and4(notEqual(e, f), notEqual(e, h));
            bvec4 interp_restriction_lv2_left = and4(notEqual(e, g), notEqual(d, g));
            bvec4 interp_restriction_lv2_up   = and4(notEqual(e, c), notEqual(b, c));

            // Detecção de bordas
            bvec4 edr = and4(
                // OTIMIZ: os termos df(i,h) e df(i,f) aparecem nos dois lados da
                // comparacao e se cancelam, entao foram removidos (mesmo resultado).
                // Equivale a weighted_distance(e,c,g,i,h,f,h,f) < weighted_distance(h,d,i,f,i,b,e,i)
                lessThan(
                    df(e, c) + df(e, g) + 4.0 * df(h, f),
                    df(h, d) + df(f, b) + 4.0 * df(e, i)
                ),
                interp_restriction_lv1
            );

            // OTIMIZ: se nenhum canto tem borda, o resultado e sempre E (igual ao original),
            // entao os smoothsteps e o resto so rodam quando existe borda.
            if (any(edr)) {
                // Curvas suaves (smoothstep) — prioridade qualidade
                // fx60 calculado só nos 2 primeiros cantos (x,y) = 10 smoothstep no total
                vec4 delta = vec4(CURVE_SOFTNESS);
                vec2 delta2 = vec2(CURVE_SOFTNESS);
                vec4 fx45 = smoothstep(Co - delta, Co + delta, fx);
                vec4 fx30 = smoothstep(Cx - delta, Cx + delta, fx_left);
                vec4 fx60 = vec4(smoothstep(Cy.xy - delta2, Cy.xy + delta2, fx_up.xy), 0.0, 0.0);


                bvec4 edr_left = and4(
                    lessThanEqual(XBR_LV2_COEFFICIENT * df(f, g), df(h, c)),
                    interp_restriction_lv2_left
                );

                bvec4 edr_up = and4(
                    greaterThanEqual(df(f, g), XBR_LV2_COEFFICIENT * df(h, c)),
                    interp_restriction_lv2_up
                );

                // Novas cores com curvas
                bvec4 nc45 = and4(edr, bvec4(fx45));
                bvec4 nc30 = and4(edr, and4(edr_left, bvec4(fx30)));
                bvec4 nc60 = and4(edr, and4(edr_up,   bvec4(fx60)));

                bvec4 nc = bvec4(
                    nc30.x || nc60.x || nc45.x,
                    nc30.y || nc60.y || nc45.y,
                    nc30.z || nc60.z || nc45.z,
                    nc30.w || nc60.w || nc45.w
                );

                vec4 final45 = vec4(nc45) * fx45;
                vec4 final30 = vec4(nc30) * fx30;
                vec4 final60 = vec4(nc60) * fx60;
                vec4 maximo  = max(max(final45, final30), final60);

                bvec4 px = lessThanEqual(df(e, f), df(e, h));

                mediump vec3 pix = E;
                float blend = 0.0;

                if (nc.x) {
                    pix = px.x ? F : H;
                    blend = maximo.x;
                } else if (nc.y) {
                    pix = px.y ? B : F;
                    blend = maximo.y;
                } else if (nc.z) {
                    pix = px.z ? D : B;
                    blend = maximo.z;
                } else if (nc.w) {
                    pix = px.w ? H : D;
                    blend = maximo.w;
                }

                // Mistura suave (curva em vez de degrau)
                res = mix(E, pix, clamp(blend, 0.0, 1.0));
            }
        }
    }

    // Ajustes de cor
    res = color_adjust(res);

    // Efeito LCD (opacidade do grid) - 0.0 = desligado
    // Só aplica quando há upscale (senão a célula seria ~1px de tela)
    if (LCD_OPACITY > 0.001 && do_upscale) {
        // tamanho de cada quadradinho em pixels de tela (X e Y)
        vec2 cell = u_texelDelta / u_pixelDelta;
        // posição dentro do quadradinho, em pixels de tela
        vec2 pos = fract(v_texcoord0 / u_texelDelta) * cell;
        // distância até a borda esquerda/topo da célula (linha com antialias)
        vec2 edge = 1.0 - smoothstep(vec2(0.0), vec2(LCD_LINE_WIDTH), pos);
        float line = max(edge.x, edge.y);
        res *= 1.0 - LCD_OPACITY * line;
    }

    gl_FragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
