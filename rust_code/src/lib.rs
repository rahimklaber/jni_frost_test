extern crate core;

use std::{collections::HashMap, panic};

use bitcoin::{SchnorrSig, SchnorrSighashType, Script, Transaction, TxOut, Witness};
use bitcoin::consensus::deserialize;
use bitcoin::hashes::hex::FromHex;
use bitcoin::psbt::serialize::{Deserialize, Serialize};
use bitcoin::schnorr::{TapTweak, TweakedPublicKey};
use bitcoin::util::sighash;
use bitcoin::util::sighash::SighashCache;
use bitcoin::util::taproot::TapTweakHash;
use bitcoin_serai::crypto::{make_even, BitcoinHram};
use modular_frost::dkg::encryption::{EncryptionKeyMessage, EncryptedMessage};
use modular_frost::dkg::{frost::{Commitments, KeyGenMachine, KeyMachine, SecretShare, SecretShareMachine}, ThresholdKeys, ThresholdParams};
use k256::{elliptic_curve::{sec1::ToEncodedPoint}, Scalar, U256};
use k256::elliptic_curve::ops::Reduce;
use modular_frost::{algorithm::{ Schnorr}, curve::{Ciphersuite, Secp256k1}, sign::{AlgorithmMachine, AlgorithmSignatureMachine, AlgorithmSignMachine, Preprocess, PreprocessMachine, SignatureMachine, SignMachine, Writable}};
use rand::rngs::OsRng;
use secp256k1::{KeyPair, Message, XOnlyPublicKey};

pub use crate::java_glue::*;

mod java_glue;

pub struct SchnorrSingleSignTest{
    pub keypair: KeyPair
}

impl SchnorrSingleSignTest{
    fn new_from_bytes(bytes: &[u8]) -> Self{
        return Self{
            keypair: KeyPair::from_seckey_slice(secp256k1::SECP256K1,bytes).unwrap()
        }
    }

    fn new() -> Self{
        // KeyPair::from
        Self::new_from_bytes(hex::decode("3e6c3783aa5cf8952b0d7f4e7942c6556b262b3d903679747f93ebd9a5730105").unwrap().as_slice())
        // return Self{
        //     keypair: KeyPair::new(secp256k1::SECP256K1,& mut OsRng),
        // }
    }
    fn get_bitcoin_encoded_key(&self) -> Vec<i8>{
        let pubkey_compressed = self.keypair.public_key().x_only_public_key().0;

        pubkey_compressed.tap_tweak(secp256k1::SECP256K1,None).0
            .serialize()
            .map(|x|x as i8).to_vec()
    }

    fn sign_tx(&self,msg_i8: &[i8], prev_out_script: &[i8]) -> Vec<i8>{
        let msg= unsafe { &*(msg_i8 as *const _  as *const [u8]) };
        let prev_out_script = unsafe { &*(prev_out_script as *const _  as *const [u8]) };
        //assume msg is serialized transaction
        let mut tx = deserialize::<Transaction>(msg).unwrap();

        let tweaked = self.keypair.clone().tap_tweak(secp256k1::SECP256K1,None);

        let script =  Script::new_v1_p2tr_tweaked(TweakedPublicKey::dangerous_assume_tweaked(tweaked.clone().to_inner().public_key().x_only_public_key().0));

        // let mut tx  = Transaction{
        //     version: 2,
        //     lock_time: PackedLockTime::ZERO,
        //     input: vec![
        //         TxIn{
        //             previous_output: OutPoint{
        //                 txid: tx.input[0].previous_output.txid,
        //                 vout: tx.input[0].previous_output.vout
        //             },
        //             script_sig: Default::default(),
        //             sequence: Default::default(),
        //             witness: Default::default(),
        //         }
        //     ],
        //     output: vec![
        //         TxOut{
        //             value: tx.output[0].value,
        //             script_pubkey: script.clone(),
        //         }
        //     ],
        // };

        // let mut psbt = Psbt::from_unsigned_tx(tx.clone()).unwrap();
        println!("script: {:?}",hex::encode(prev_out_script));
        let hash = SighashCache::new(&tx).taproot_key_spend_signature_hash(
            0,
            &sighash::Prevouts::All(&[TxOut {
                value: 1000000 ,
                script_pubkey: script.clone(),
            }]),
            SchnorrSighashType::Default,
        ).unwrap();


        println!("hash: {:?}",hex::encode(hash.as_ref()));

        dbg!(tx.clone());




        println!("tweaked priv: {:?}",hex::encode(tweaked.clone().to_inner().secret_bytes()));
        let msg = Message::from_slice(hash.as_ref()).unwrap();
        // let msg = Message::from_slice(hex::decode("f2aa12301f75e976d48925cfc870f593f249a42774c4cb8a1fe2586f899137f5").unwrap().as_slice()).unwrap();
        let sig = tweaked.clone().to_inner().sign_schnorr(msg.clone());
        let bitcoinsig = SchnorrSig{
            sig,
            hash_ty: SchnorrSighashType::Default,
        };
        let mut tx_to_sign = tx.clone();

        let mut witness = Witness::new();
        witness.push(bitcoinsig.serialize().as_slice());
        tx_to_sign.input[0].witness = witness;


        tx_to_sign.verify(|_|{
            Some(TxOut {
                value: 1000000,
                script_pubkey: Script::deserialize(prev_out_script).unwrap(),
            })
        }).expect("failed to veirfy tx");

        println!("bitcoin rust serialized: {:?}",hex::encode(tx_to_sign.clone().serialize()));


        sig.verify(&msg,&tweaked.clone().to_inner().x_only_public_key().0).unwrap();



         bitcoinsig.serialize()
            .into_iter().map(|x|x as i8).collect()
            // .as_slice()
            // .into_iter()
            // .map(|&x|x as i8).to_vec()
    }
}

pub struct SchnorrKeyGenWrapper{
    pub key_params: ThresholdParams,
    pub key_gen_machine: Option<KeyGenMachine<Secp256k1>>,
    pub secret_share_machine: Option<SecretShareMachine<Secp256k1>>,
    pub key_machine: Option<KeyMachine<Secp256k1>>
}

pub struct SchnorrKeyWrapper{
    pub key: ThresholdKeys<Secp256k1>
}

impl SchnorrKeyWrapper {
    fn new () -> SchnorrKeyWrapper{
        panic!();
    }

    fn get_bitcoin_encoded_key(&self) -> Vec<i8>{
        let mut key = self.key.clone();
        let pubkey_compressed = key.group_key().to_encoded_point(true);
        println!("raw pk {:?}",hex::encode(pubkey_compressed.x().unwrap()));
        let mut pubkey_obj =
            secp256k1::XOnlyPublicKey::from_slice(&pubkey_compressed.x().to_owned().unwrap()).unwrap();
        let tweak = TapTweakHash::from_key_and_tweak(pubkey_obj, None).to_scalar();
        let tweak_parsed = Scalar::from_uint_reduced(U256::from_be_slice(tweak.to_be_bytes().as_slice()));
        let pub_tweak = tweak_parsed;
        key = key.offset(pub_tweak.clone());
        let test_pub = key.group_key();
        let (_,test_offset) = make_even(test_pub);
        key = key.offset(Scalar::from(Scalar::from(test_offset)));

        let pubkey_compressed = key.group_key().to_encoded_point(true);
        XOnlyPublicKey::from_slice(pubkey_compressed.x().unwrap()).unwrap()
            .serialize()
            .map(|x|x as i8).to_vec()
        // bitcoin::taproot
        // pubkey_compressed.x().to_owned().unwrap()
        // .map(|&x|x as i8).to_vec()
    }
    
}

pub struct SignResult1{
    pub wrapper: SchnorrSignWrapper,
    pub preprocess: Vec<u8>
}

impl SignResult1{
    fn new() -> SignResult1{
        panic!()
    }

    fn get_preprocess(&self) -> Vec<i8> {
        self.preprocess.iter().map(|&x| x as i8).collect()
    }
    fn get_wrapper(res: SignResult1) -> SchnorrSignWrapper{
        res.wrapper
    }
}


pub struct SignResult2{
    pub wrapper: SchnorrSignWrapper,
    pub share: Vec<u8>
}

impl SignResult2{
    fn new() -> SignResult2{
        panic!()
    }

    fn get_share(&self) -> Vec<i8>{
        self.share.iter().map(|&x| x as i8).collect()
    }
    fn get_wrapper(res: SignResult2) -> SchnorrSignWrapper{
        res.wrapper
    }
}

pub struct SignParams2{
    pub commitments: HashMap<u16, Vec<u8>>
}

impl SignParams2{
    fn new() -> SignParams2{
        Self{
            commitments: HashMap::default(),
        }
    }

    fn add_commitment_from_user(& mut self, user: u16, commitment: &[i8]){
        let buf_u8 : Vec<u8> = commitment.iter().map(|&x| x as u8).collect();
        
        self.commitments.insert(user, buf_u8);
    }
}

pub struct SignParams3{
    pub shares: HashMap<u16, Vec<u8>>
}

impl SignParams3{
    fn new() -> SignParams3{
        Self{
            shares: HashMap::default(),
        }
    }

    fn add_share_of_user(& mut self, user: u16, share: &[i8]){
        let buf_u8 : Vec<u8> = share.iter().map(|&x| x as u8).collect();
        
        self.shares.insert(user, buf_u8);
    }
}

pub struct SchnorrSignWrapper{
    pub algo_machine: Option<AlgorithmMachine<Secp256k1,Schnorr<Secp256k1,BitcoinHram>>>,
    pub sign_machine: Option<AlgorithmSignMachine<Secp256k1,Schnorr<Secp256k1,BitcoinHram>>>,
    pub signature_machine: Option<AlgorithmSignatureMachine<Secp256k1,Schnorr<Secp256k1,BitcoinHram>>>,
    pub tweak: Scalar,
    pub threshold_keys: ThresholdKeys<Secp256k1>,
    pub msg: Vec<u8>
}

impl SchnorrSignWrapper{
    fn new() -> SchnorrSignWrapper{
        panic!()
    }

    fn new_instance_for_signing(_key: & SchnorrKeyWrapper, threshold: u32) -> SchnorrSignWrapper{


        let mut key = _key.key.clone();
        let pubkey_compressed = key.group_key().to_encoded_point(true);
        println!("raw pk {:?}",hex::encode(pubkey_compressed.x().unwrap()));
        let mut pubkey_obj =
            secp256k1::XOnlyPublicKey::from_slice(&pubkey_compressed.x().to_owned().unwrap()).unwrap();
            let tweak = TapTweakHash::from_key_and_tweak(pubkey_obj, None).to_scalar();
        let tweak_parsed = Scalar::from_uint_reduced(U256::from_be_slice(tweak.to_be_bytes().as_slice()));
        let pub_tweak = tweak_parsed;
        key = key.offset(pub_tweak.clone());
        let test_pub = key.group_key();
        let (_,test_offset) = make_even(test_pub);
        key = key.offset(Scalar::from(Scalar::from(test_offset)));
        // if test_offset != 0{
        //     panic!("tweaked pub key not even")
        // }
        // let (even_key,offset) = make_even(key.group_key());
        // if offset == 0{
        //     println!("key is even");
        // }else{
        //     println!("key is odd offset: {:?}",offset);
        // }
        // key = key.offset(Scalar::from(offset));
        Self{
            algo_machine: Some(
                AlgorithmMachine::new(
                Schnorr::<Secp256k1, BitcoinHram>::default(),
                key.clone(),
            ).unwrap()),
            sign_machine: None,
            signature_machine: None,
            tweak: pub_tweak.clone() + Scalar::from(test_offset),
            threshold_keys: key.clone(),
            msg: Vec::new()
        }
    }

    fn sign_1_preprocess(wrapper: SchnorrSignWrapper) -> SignResult1{
        let (sign_machine, preprocess) = wrapper.algo_machine.unwrap().preprocess(& mut OsRng);
        let mut buffer: Vec<u8> = vec![];
        preprocess.write(& mut buffer);

        SignResult1{
            wrapper: Self{
                algo_machine: None,
                sign_machine: Some(sign_machine),
                signature_machine: None,
                tweak: wrapper.tweak,
                threshold_keys: wrapper.threshold_keys,
                msg: wrapper.msg
            },
            preprocess: buffer,
        }
    }

    fn sign_2_sign_bitcoin(wrapper: SchnorrSignWrapper, params: SignParams2, msg_i8: &[i8], prev_out_script: &[i8]) -> SignResult2 {
        let msg= unsafe { &*(msg_i8 as *const _  as *const [u8]) };
        let prev_out_script = unsafe { &*(prev_out_script as *const _  as *const [u8]) };
        //assume msg is serialized transaction
        let mut tx = deserialize::<Transaction>(msg).unwrap();
        let script = Script::from_hex(hex::encode(prev_out_script).as_str()).unwrap();
        // let script =  Script::new_v1_p2tr_tweaked(TweakedPublicKey::dangerous_assume_tweaked(tweaked.clone().to_inner().public_key().x_only_public_key().0));

        // let mut tx  = Transaction{
        //     version: 2,
        //     lock_time: PackedLockTime::ZERO,
        //     input: vec![
        //         TxIn{
        //             previous_output: OutPoint{
        //                 txid: tx.input[0].previous_output.txid,
        //                 vout: tx.input[0].previous_output.vout
        //             },
        //             script_sig: Default::default(),
        //             sequence: Default::default(),
        //             witness: Default::default(),
        //         }
        //     ],
        //     output: vec![
        //         TxOut{
        //             value: tx.output[0].value,
        //             script_pubkey: tx.output[0].script_pubkey.clone(),
        //         }
        //     ],
        // };

        // let mut psbt = Psbt::from_unsigned_tx(tx.clone()).unwrap();
        println!("script: {:?}",hex::encode(prev_out_script));
        let hash = SighashCache::new(&tx).taproot_key_spend_signature_hash(
            0,
            &sighash::Prevouts::All(&[TxOut {
                value: 1000000 ,
                script_pubkey: script.clone(),
            }]),
            SchnorrSighashType::Default,
        ).unwrap();

        println!("hash to sign: {:?}",hex::encode(hash.as_ref()));

        let sign_machine = wrapper.sign_machine.unwrap();
        let map : HashMap<u16,Preprocess<_,_>> =params.commitments.iter()
        .map(|(&user,buf)|{
            let preprocess = sign_machine.read_preprocess::<&[u8]>(&mut buf.as_ref()).unwrap();
            (user, preprocess)
        }).collect();

        let (signature_machine, sig_share) = sign_machine.sign(map, hash.as_ref()).unwrap();
        let mut buf: Vec<u8> = vec![];
        sig_share.write(& mut buf);

        SignResult2{
            wrapper: Self{
                algo_machine: None,
                sign_machine: None,
                signature_machine: Some(signature_machine),
                tweak: wrapper.tweak,
                threshold_keys: wrapper.threshold_keys,
                msg: hash.as_ref().iter().map(|&x|x).collect()
            },
            share: buf,
        }
    }

    fn sign_2_sign_normal(wrapper: SchnorrSignWrapper, params: SignParams2, msg_i8: &[i8]) -> SignResult2 {
        let msg= unsafe { &*(msg_i8 as *const _  as *const [u8]) };

        let sign_machine = wrapper.sign_machine.unwrap();
        let map : HashMap<u16,Preprocess<_,_>> =params.commitments.iter()
            .map(|(&user,buf)|{
                let preprocess = sign_machine.read_preprocess::<&[u8]>(&mut buf.as_ref()).unwrap();
                (user, preprocess)
            }).collect();

        let (signature_machine, sig_share) = sign_machine.sign(map, msg).unwrap();
        let mut buf: Vec<u8> = vec![];
        sig_share.write(& mut buf);

        SignResult2{
            wrapper: Self{
                algo_machine: None,
                sign_machine: None,
                signature_machine: Some(signature_machine),
                tweak: wrapper.tweak,
                threshold_keys: wrapper.threshold_keys,
                msg: msg.iter().map(|&x|x).collect()
            },
            share: buf,
        }
    }

    fn sign_3_complete(wrapper: SchnorrSignWrapper, params: SignParams3) -> Vec<i8>{
        let signature_machine = wrapper.signature_machine.unwrap();



        let shares_map = params.shares.iter()
        .map(|(&user,buf)|{
            let sig = signature_machine.read_share::<&[u8]>(&mut buf.as_ref()).unwrap();
            (user,sig)
        }).collect();
        let mut _sig = signature_machine.complete(shares_map).unwrap();
        //
        let mut offset = 0;
        (_sig.R, offset) = make_even(_sig.R);
        _sig.s += Scalar::from(offset);
        // wrapper.tweak.clone().
        // _sig.R += Secp256k1::generator()* Scalar::from(wrapper.tweak.clone())   ;
        // _sig.s += Scalar::from(wrapper.tweak.clone());
        // _sig.R += Secp256k1::generator()* Scalar::from(wrapper.tweak.clone())   ;
        // _sig.s += Scalar::from(wrapper.tweak.clone());

        // mae compatible wth bip340
        let sig = secp256k1::schnorr::Signature::from_slice(&_sig.serialize()[1..65]).unwrap();

        let verify_key = XOnlyPublicKey::from_slice(wrapper.threshold_keys.group_key().to_encoded_point(true).x().unwrap()).unwrap();
        println!("veirfy_key : {:?}",hex::encode(verify_key.serialize()));
        let verify_msg = wrapper.msg.clone();
        println!("msg len: {:?}",verify_msg.len());
        let verify_msg_obj = Message::from_slice(verify_msg.as_slice()).unwrap();
        sig.verify(&verify_msg_obj,&verify_key).expect("sig is invalid");

        println!("sig size: {:?}",_sig.serialize().len());
        let mut buf = &_sig.serialize()[1..65];
        return buf.iter().map(|&x| x as i8).collect();
    }
}

// pub struct SchnorrWrapper{
//     pub keygen: SchnorrKeyGenWrapper
// }

pub struct ResultKeygen1{
    pub keygen: SchnorrKeyGenWrapper,
    pub res: Vec<i8>
}

impl ResultKeygen1 {
    fn new(keygen: SchnorrKeyGenWrapper, res: &[i8]) -> Self{
        ResultKeygen1 { keygen, res: res.into() }
    }

    fn get_keygen(result: ResultKeygen1) -> SchnorrKeyGenWrapper{
        result.keygen
    }

    fn get_res(&self) -> Vec<i8>{
        self.res.clone()
    }
}

pub struct ParamsKeygen2{
    pub commitment_user_indices: Vec<u16>,
    pub commitments: Vec<Vec<i8>>
}

impl ParamsKeygen2{
    fn new () -> Self{
        ParamsKeygen2 { commitment_user_indices: vec![], commitments: vec![] }
    }

    fn add_commitment_from_user(&mut self,user: u16, commitment: &[i8]){
        self.commitment_user_indices.push(user);
        self.commitments.push(commitment.into());
    }
}

pub struct ParamsKeygen3{
    pub shares_user_indices: Vec<u16>,
    pub shares: Vec<Vec<i8>>
}

impl ParamsKeygen3{
    fn new () -> Self{
        ParamsKeygen3 { shares_user_indices: vec![], shares: vec![] }
    }

    fn add_share_from_user(&mut self,user: u16, shares: &[i8]){
        self.shares_user_indices.push(user);
        self.shares.push(shares.into());
    }
}

pub struct ResultKeygen2{
    pub keygen: SchnorrKeyGenWrapper,
    pub user_indices: Vec<u16>,
    pub shares: Vec<Vec<u8>>
}

impl ResultKeygen2 {
    fn new() -> ResultKeygen2{
        ResultKeygen2{
            keygen: SchnorrKeyGenWrapper::new(1, 1, 1, "".into()),
            user_indices: vec![],
            shares: vec![],
        }
    }
    fn get_keygen(result: ResultKeygen2) -> SchnorrKeyGenWrapper{
        result.keygen
    }
    fn get_user_indices(&self) -> Vec<i32>{
        self.user_indices.iter().map(|&x| x as i32).collect()
    }
    fn get_shares_at(&self, index: usize) -> Vec<i8>{
        self.shares.get(index).unwrap().iter().map(|&x| x as i8).collect()
    }
}

impl SchnorrKeyGenWrapper{
    fn new(threshold: u16, n: u16, index: u16, context: String) -> SchnorrKeyGenWrapper {
        // println!("rip {:?}{:?}{:?}", threshold, n, index);
        let key_params = ThresholdParams::new(threshold,n,index).unwrap();
        SchnorrKeyGenWrapper{
            key_params: key_params,
            key_gen_machine: Some(KeyGenMachine::<Secp256k1>::new(key_params.clone(), context.into())),
            secret_share_machine: None,
            key_machine: None
        }
    }
    // create commitments for the dkg
    fn key_gen_1_create_commitments(wrapper: SchnorrKeyGenWrapper) -> ResultKeygen1{
        let (secret_share_machine, commitments) = wrapper.key_gen_machine.unwrap().generate_coefficients(& mut OsRng);
        let mut buffer: Vec<u8> = vec![];
        commitments.write(&mut buffer);

        let new_wrapper = Self{
            key_params: wrapper.key_params.clone(),
            key_gen_machine: None,
            secret_share_machine: Some(secret_share_machine),
            key_machine: None
        };

        ResultKeygen1{
            keygen: new_wrapper,
            res: buffer.into_iter().map(|x| x as i8).collect()
        }

    }

    //Note, the return is a vec with tuples(for which index the share is for, serialized share)
    fn key_gen_2_generate_shares(wrapper: SchnorrKeyGenWrapper, param_wrapper: ParamsKeygen2) -> ResultKeygen2 {
        let mut commitments_map = HashMap::<u16,_>::new();
        let commitments = param_wrapper.commitment_user_indices.iter().zip(param_wrapper.commitments);
        commitments
        .for_each(|(i,bytes_i8)|{
            let bytes: Vec<u8> = bytes_i8.iter().map(|&x| x as u8).collect();
            let commitment = EncryptionKeyMessage::<Secp256k1,Commitments<Secp256k1>>::read::<&[u8]>(& mut bytes.as_ref(), wrapper.key_params.clone()).unwrap();
            commitments_map.insert(*i, commitment);
        });

        let (key_machine, mut shares) = wrapper.secret_share_machine
            .unwrap().generate_secret_shares(&mut OsRng,commitments_map).unwrap();

        let mut res_wrapper = ResultKeygen2::new();

        shares
        .iter_mut()
        .for_each(|(i,secret_share)|{
            let mut buffer: Vec<u8> = vec![];
            secret_share.write(&mut buffer);
            res_wrapper.user_indices.push(*i);
            res_wrapper.shares.push(buffer);
            // (*i, buffer)
        });

        let new_wrapper = Self{
            key_params: wrapper.key_params.clone(),
            key_gen_machine: None,
            secret_share_machine: None,
            key_machine: Some(key_machine)
        };

        res_wrapper.keygen = new_wrapper;
        res_wrapper
    }

    // shares are the shares the others created for us
    fn key_gen_3_complete(wrapper: SchnorrKeyGenWrapper, param_wrapper: ParamsKeygen3) -> SchnorrKeyWrapper {
        let mut shares_map = HashMap::<u16,_>::new();
        let shares = param_wrapper.shares_user_indices.iter().zip(param_wrapper.shares);
        shares
        .for_each(|(from,bytes_i8)|{
            let bytes: Vec<u8> = bytes_i8.iter().map(|&x| x as u8).collect();
            let share = EncryptedMessage::<Secp256k1, SecretShare<<modular_frost::curve::Secp256k1 as Ciphersuite>::F>>::read::<&[u8]>(&mut bytes.as_ref(), wrapper.key_params.clone()).unwrap();
            shares_map.insert(*from, share);
        });

        let blame_machine = wrapper.key_machine.unwrap().calculate_share(&mut OsRng, shares_map).unwrap();

        let core = blame_machine.complete();

        let mut key = ThresholdKeys::new(core);


        SchnorrKeyWrapper{
            key:key
        }
    }
}

// impl SchnorrWrapper{
    // fn new(threshold: u16, n: u16, index: u16, context: String) -> Self{
    //     SchnorrWrapper { keygen: SchnorrKeyGenWrapper::new(threshold, n, index, context) }
    // }

    // fn key_gen_1_create_commitments(mut self) -> Vec<i8>{
    //     let (new_key_gen, ret) = self.keygen.key_gen_1_create_commitments();
    //     self.keygen = new_key_gen;

    //     // compiler should optimize
    //     let vec_i8: Vec<i8> = ret.into_iter().map(|x| x as i8).collect();
    //     vec_i8.into()
    // }
    // fn key_gen_2_generate_shares(mut self, mut commitments: Vec<(u16, Vec<u8>)>) -> Vec<(u16,Vec<u8>)> {
    //     let (new_keygen,res) = self.keygen.key_gen_2_generate_shares(commitments);
    //     self.keygen = new_keygen;
    //     res
    // }
    // fn key_gen_3_complete(mut self, mut shares: Vec<(u16,Vec<u8>)>) {
    //     self.keygen.key_gen_3_complete(shares);
    // }
// }

pub mod test;
