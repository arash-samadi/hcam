// extracted from libposteffect.so @ 0x21501
// kind: vertex

attribute vec4 a_vertexCoord;            attribute vec2 a_textureCoord;            attribute vec2 a_blurTexCoord;            varying vec2 v_textureCoord;            varying vec2 v_blurTexCoord;                                                    void main()                             {                                            gl_Position = a_vertexCoord;                v_textureCoord = a_textureCoord;            v_blurTexCoord = a_blurTexCoord;                                                    return;                             }
