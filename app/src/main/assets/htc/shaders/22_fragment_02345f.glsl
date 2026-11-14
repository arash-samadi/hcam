// extracted from libposteffect.so @ 0x2345f
// kind: fragment

precision mediump float;                                            uniform sampler2D s_texture0;                                        varying vec2 v_texCoord;                                                                                                                void main()                                                         {                                                                        gl_FragColor = texture2D(s_texture0, v_texCoord);                    return;                                                         }
