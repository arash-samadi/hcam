// extracted from libposteffect.so @ 0x21fcf
// kind: vertex

attribute vec4 a_vertexCoord;                                        attribute vec2 a_textureCoord;                                        varying vec2 v_texCoord;                                                                                                                void main()                                                         {                                                                        gl_Position = a_vertexCoord;                                        v_texCoord = a_textureCoord;                                        return;                     }
