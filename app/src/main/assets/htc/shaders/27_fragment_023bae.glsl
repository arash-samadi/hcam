// extracted from libposteffect.so @ 0x23bae
// kind: fragment

precision mediump float;            uniform sampler2D s_texture0;    uniform mat4 u_mat_yuv2rgb;varying vec2 v_texCoord;            void main()                            {                                        gl_FragColor = u_mat_yuv2rgb *  texture2D(s_texture0, v_texCoord);                    return;                                                }
