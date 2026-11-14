// extracted from libposteffect.so @ 0x1f8bc
// kind: vertex

attribute vec4 a_vertexCoord;                attribute vec2 a_textureCoord;                                                            varying vec4 v_vertexCoord;                 varying vec2 v_textureCoord;                                                                                                        void main()                                 {                                                gl_Position = a_vertexCoord;                    v_vertexCoord = a_vertexCoord;                                                                v_textureCoord = a_textureCoord;                                                            return;                                 }
