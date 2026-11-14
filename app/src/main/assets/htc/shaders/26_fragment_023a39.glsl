// extracted from libposteffect.so @ 0x23a39
// kind: fragment

precision mediump float;            uniform sampler2D s_texture0;            uniform sampler2D s_texture1;    uniform mat4 u_mat_yuv2rgb;varying vec2 v_texCoord;            void main()                            {                                        vec4 cY,cCrCb;              cY = texture2D(s_texture0, v_texCoord);            cCrCb = texture2D(s_texture1,v_texCoord);            gl_FragColor = u_mat_yuv2rgb *  vec4(cY.r, cCrCb.r, cCrCb.a, 1.0);                    return;                                                }
