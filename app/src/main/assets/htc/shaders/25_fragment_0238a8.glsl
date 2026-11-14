// extracted from libposteffect.so @ 0x238a8
// kind: fragment

precision mediump float;            uniform sampler2D s_texture0;        uniform sampler2D s_dotMask;        varying vec2 v_texCoord;            varying vec2 v_maskTexCoord;        void main()                            {                                        vec4 p;                             vec4 mask;                                                                p = texture2D(s_texture0, v_texCoord);                    mask = texture2D(s_dotMask, v_maskTexCoord);            p = p * mask;                    gl_FragColor = p;                    return;                            }
